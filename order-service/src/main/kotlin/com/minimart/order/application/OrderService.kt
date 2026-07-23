package com.minimart.order.application

import com.minimart.order.application.dto.CancelOrderCommand
import com.minimart.order.application.dto.GetOrderCommand
import com.minimart.order.application.dto.ListOrdersCommand
import com.minimart.order.application.dto.PlaceOrderCommand
import com.minimart.order.domain.exception.DuplicateIdempotencyKeyException
import com.minimart.order.domain.exception.ForbiddenActionException
import com.minimart.order.domain.exception.InsufficientStockException
import com.minimart.order.domain.exception.NotEligibleToOrderException
import com.minimart.order.domain.exception.OrderNotCancellableException
import com.minimart.order.domain.exception.OrderNotFoundException
import com.minimart.order.domain.exception.OrderValidationException
import com.minimart.order.domain.exception.StockFailureDetail
import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderItem
import com.minimart.order.domain.model.OrderStatus
import com.minimart.order.domain.model.OrderSummary
import com.minimart.order.domain.model.RoleCode
import com.minimart.order.domain.port.CatalogClientPort
import com.minimart.order.domain.port.IdempotencyPort
import com.minimart.order.domain.port.IdentityClientPort
import com.minimart.order.domain.port.OrderEventPublisherPort
import com.minimart.order.domain.port.OrderRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Use-case interactor for Phase-5/Phase-6's order endpoints. Phase 5 is
 * the riskiest integration point in the system (that phase's doc): a
 * single checkout request depends on identity-service and catalog-service
 * both being reachable and correct, on top of order-service's own
 * Postgres write.
 *
 * A reserved-then-released compensation loop (ORD-007) and an
 * idempotency-key race fallback (ORD-009) are the two pieces of real
 * control flow beyond a normal CRUD use case in placeOrder; both are
 * commented inline at the point they happen, not just in this class-level
 * summary. cancelOrder (Phase 6) is comparatively simple — one status
 * transition plus a best-effort stock release, see that method's own
 * comment for the one real judgment call it makes.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepositoryPort,
    private val identityClientPort: IdentityClientPort,
    private val catalogClientPort: CatalogClientPort,
    private val idempotencyPort: IdempotencyPort,
    private val orderEventPublisherPort: OrderEventPublisherPort,
    private val meterRegistry: MeterRegistry,
) : OrderUseCase {

    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    override fun placeOrder(command: PlaceOrderCommand): Order {
        // ORD-002 (at least one item) is enforced declaratively on PlaceOrderRequest
        // (`@NotEmpty`) — Bean Validation already rejects an empty list before this method is
        // ever called, mirroring catalog-service's own precedent of not re-checking a
        // `@NotBlank` field's presence again in its service layer. ORD-004, below, can't be
        // expressed as a single-field annotation (it's a cross-item constraint), so it's checked
        // here instead.
        val requestedProductIds = command.items.map { it.productId }
        if (requestedProductIds.toSet().size != requestedProductIds.size) {
            throw OrderValidationException("A given product can appear at most once per order.")
        }

        // ORD-009 fast path: a cached hit means this exact idempotency key already produced an
        // order — return it without re-running any identity/catalog gRPC calls at all.
        idempotencyPort.findOrderId(command.idempotencyKey)?.let { cachedOrderId ->
            orderRepository.findById(cachedOrderId)?.let { cachedOrder ->
                logger.info("Idempotent replay: idempotencyKey={} -> existing order id={}", command.idempotencyKey, cachedOrder.id)
                meterRegistry.counter(METRIC_PLACE, "result", "idempotent_replay").increment()
                return cachedOrder
            }
            // Cache pointed at an order id Postgres no longer has anything for — shouldn't
            // happen (orders are never deleted), but fall through to normal processing rather
            // than trust a dangling cache entry.
            logger.warn("Idempotency cache had orderId={} for key={} but no such order exists; reprocessing", cachedOrderId, command.idempotencyKey)
        }

        // ORD-001 — checked before touching catalog at all.
        if (!identityClientPort.isEligibleToOrder(command.customerId)) {
            logger.warn("Rejected checkout: customerId={} is not eligible to order", command.customerId)
            meterRegistry.counter(METRIC_PLACE, "result", "not_eligible").increment()
            throw NotEligibleToOrderException()
        }

        val reservation = reserveAllOrCompensate(command)

        val now = Instant.now()
        val orderItems = reservation.map {
            OrderItem(
                id = UUID.randomUUID(),
                productId = it.productId,
                productNameSnapshot = it.name,
                unitPriceSnapshot = it.unitPrice,
                quantity = it.quantity,
            )
        }
        val totalAmount = orderItems.fold(BigDecimal.ZERO) { sum, item -> sum + item.lineTotal }
        val candidateOrder = Order(
            id = UUID.randomUUID(),
            customerId = command.customerId,
            status = OrderStatus.PLACED,
            totalAmount = totalAmount,
            idempotencyKey = command.idempotencyKey,
            items = orderItems,
            createdAt = now,
            updatedAt = now,
        )

        val savedOrder = try {
            orderRepository.insert(candidateOrder)
        } catch (duplicateKey: DuplicateIdempotencyKeyException) {
            // ORD-009's real race: two near-simultaneous requests with the same key both missed
            // the Redis check above and both reserved stock; Postgres's UNIQUE constraint is what
            // actually decides exactly one wins (GEN-001's same principle, applied to checkout
            // retries rather than stock). This attempt lost — its reservation must be released
            // exactly like a failed multi-item reservation would be, then the winner's order (the
            // one that actually got persisted) is returned instead, matching the doc's own
            // "retry returns the already-created order" contract.
            logger.info("Idempotency key race: {} lost to a concurrent request; releasing this attempt's reservations", command.idempotencyKey)
            releaseAll(reservation)
            meterRegistry.counter(METRIC_PLACE, "result", "idempotency_race_lost").increment()
            orderRepository.findByIdempotencyKey(command.idempotencyKey)
                ?: throw IllegalStateException(
                    "idempotency_key ${command.idempotencyKey} was rejected as a duplicate but no order with that key can be found",
                )
        }

        idempotencyPort.remember(command.idempotencyKey, savedOrder.id)

        // Publish only reaches here once orderRepository.insert has returned — i.e. only after
        // its own transaction has already committed (see OrderRepositoryAdapter.insert), which is
        // what satisfies ARCHITECTURE.md §5's "publish after commit" without needing a separate
        // @TransactionalEventListener(AFTER_COMMIT) indirection: this method itself is not
        // @Transactional, so there's no ambient transaction for the publish call to (incorrectly)
        // become part of.
        orderEventPublisherPort.publishOrderPlaced(savedOrder)

        logger.info("Order placed: id={} customerId={} totalAmount={}", savedOrder.id, savedOrder.customerId, savedOrder.totalAmount)
        meterRegistry.counter(METRIC_PLACE, "result", "success").increment()
        return savedOrder
    }

    override fun getOrder(command: GetOrderCommand): Order {
        val order = orderRepository.findById(command.orderId) ?: run {
            meterRegistry.counter(METRIC_GET, "result", "not_found").increment()
            throw OrderNotFoundException(command.orderId)
        }

        // ORD-013: an Administrator may view any order; a Customer only their own — and a
        // mismatch here is reported identically to "doesn't exist" (404, not 403), so a Customer
        // probing another customer's order id learns nothing about whether it's real.
        if (command.callerRole != RoleCode.ADMIN && order.customerId != command.callerId) {
            logger.info("Order id={} does not belong to caller id={}; reporting as not found (ORD-013)", order.id, command.callerId)
            meterRegistry.counter(METRIC_GET, "result", "not_owned").increment()
            throw OrderNotFoundException(command.orderId)
        }

        meterRegistry.counter(METRIC_GET, "result", "success").increment()
        return order
    }

    override fun listOrders(command: ListOrdersCommand): List<OrderSummary> {
        val effectiveCustomerId = when {
            command.callerRole == RoleCode.ADMIN -> command.customerIdFilter
            command.customerIdFilter == null || command.customerIdFilter == command.callerId -> command.callerId
            else -> {
                logger.warn("Forbidden: customer id={} requested another customer's order history (id={})", command.callerId, command.customerIdFilter)
                meterRegistry.counter(METRIC_LIST, "result", "forbidden").increment()
                throw ForbiddenActionException("You can only view your own order history.")
            }
        }

        meterRegistry.counter(METRIC_LIST, "result", "success").increment()
        return orderRepository.findSummaries(effectiveCustomerId)
    }

    override fun cancelOrder(command: CancelOrderCommand): Order {
        val order = orderRepository.findById(command.orderId) ?: run {
            meterRegistry.counter(METRIC_CANCEL, "result", "not_found").increment()
            throw OrderNotFoundException(command.orderId)
        }

        // ORD-011 grants cancellation only to the order's own customer — no Administrator
        // exception exists for this action (unlike ORD-013's explicit one for viewing) — so this
        // check ignores the caller's role entirely, reporting a mismatch identically to "doesn't
        // exist" (404), same posture as getOrder.
        if (order.customerId != command.callerId) {
            logger.info("Order id={} does not belong to caller id={}; reporting as not found (ORD-011)", order.id, command.callerId)
            meterRegistry.counter(METRIC_CANCEL, "result", "not_owned").increment()
            throw OrderNotFoundException(command.orderId)
        }

        if (order.status != OrderStatus.PLACED) {
            meterRegistry.counter(METRIC_CANCEL, "result", "not_cancellable").increment()
            throw OrderNotCancellableException(order.id, order.status)
        }

        // ORD-012: release every line item's quantity back to available stock. A release failure
        // is logged loudly (a real inventory-consistency concern) but deliberately does not block
        // the cancellation itself — mirroring placeOrder's own "the customer-facing outcome must
        // not depend on a downstream system's health" posture (there, the order.placed publish;
        // here, ORD-011's unconditional "a Customer can cancel their own order at any time"). Not
        // attempting to undo an already-succeeded release for an earlier item if a later one
        // fails, unlike placeOrder's all-or-nothing reservation — a partially-restored stock
        // count is a bookkeeping issue for ops to fix, not a customer-facing correctness problem
        // the way an unrejected over-sold order would be.
        for (item in order.items) {
            val released = catalogClientPort.releaseStock(item.productId, item.quantity)
            if (!released) {
                logger.error(
                    "Failed to release stock while cancelling order id={}: productId={} quantity={} — " +
                        "stock may now be incorrectly low",
                    order.id,
                    item.productId,
                    item.quantity,
                )
            }
        }

        val cancelledOrder = orderRepository.update(order.copy(status = OrderStatus.CANCELLED, updatedAt = Instant.now()))

        // Same "after commit" reasoning as placeOrder's publish call — see that method's comment.
        orderEventPublisherPort.publishOrderCancelled(cancelledOrder)

        logger.info("Order cancelled: id={} customerId={}", cancelledOrder.id, cancelledOrder.customerId)
        meterRegistry.counter(METRIC_CANCEL, "result", "success").increment()
        return cancelledOrder
    }

    /** One successfully reserved line item, carrying what's needed for both the order row and a potential compensating release. */
    private data class Reservation(val productId: UUID, val name: String, val unitPrice: BigDecimal, val quantity: Int)

    /**
     * ORD-007: attempts GetProduct + ReserveStock for every line item —
     * deliberately not short-circuiting on the first failure, so a
     * customer with multiple problem items learns about all of them from
     * one response (see InsufficientStockException kdoc). If anything
     * failed, every reservation that DID succeed in this same request is
     * released again before the exception is thrown — "no partial
     * deduction survives a rejected order" (Phase-5 doc).
     */
    private fun reserveAllOrCompensate(command: PlaceOrderCommand): List<Reservation> {
        val succeeded = mutableListOf<Reservation>()
        val failures = mutableListOf<StockFailureDetail>()

        for (item in command.items) {
            val product = catalogClientPort.getProduct(item.productId)
            if (product == null) {
                // Missing or Deactivated (CAT-008) — catalog-service's GetProduct already
                // collapses both; from here it's indistinguishable from "zero available".
                failures += StockFailureDetail(item.productId, item.quantity, 0)
                continue
            }
            if (product.stockAvailable < item.quantity) {
                // Pre-check against the GetProduct-time count — avoids an always-doomed
                // ReserveStock round trip. ReserveStock's own atomic result below is still what
                // actually decides success under concurrent checkouts (GEN-001); this is purely
                // an optimization for the already-obviously-insufficient case.
                failures += StockFailureDetail(item.productId, item.quantity, product.stockAvailable)
                continue
            }
            if (!catalogClientPort.reserveStock(item.productId, item.quantity)) {
                // A genuine race: stock looked sufficient at GetProduct time but ReserveStock's
                // atomic check failed anyway (a concurrent order won it first, GEN-001). The
                // GetProduct-time count is the best "available" figure this call has without
                // another round trip; it may be very slightly stale by now.
                failures += StockFailureDetail(item.productId, item.quantity, product.stockAvailable)
                continue
            }
            succeeded += Reservation(item.productId, product.name, product.unitPrice, item.quantity)
        }

        if (failures.isNotEmpty()) {
            releaseAll(succeeded)
            meterRegistry.counter(METRIC_PLACE, "result", "insufficient_stock").increment()
            throw InsufficientStockException(failures)
        }

        return succeeded
    }

    private fun releaseAll(reservations: List<Reservation>) {
        for (reservation in reservations) {
            val released = catalogClientPort.releaseStock(reservation.productId, reservation.quantity)
            if (!released) {
                logger.error(
                    "Failed to release stock during checkout rollback: productId={} quantity={} — " +
                        "stock may now be incorrectly reduced with no order to account for it",
                    reservation.productId,
                    reservation.quantity,
                )
            }
        }
    }

    private companion object {
        const val METRIC_PLACE = "order.orders.place"
        const val METRIC_GET = "order.orders.get"
        const val METRIC_LIST = "order.orders.list"
        const val METRIC_CANCEL = "order.orders.cancel"
    }
}
