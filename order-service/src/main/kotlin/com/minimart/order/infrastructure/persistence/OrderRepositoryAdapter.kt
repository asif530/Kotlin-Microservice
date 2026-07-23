package com.minimart.order.infrastructure.persistence

import com.minimart.order.domain.exception.DuplicateIdempotencyKeyException
import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderSummary
import com.minimart.order.domain.port.OrderRepositoryPort
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Adapter implementing the domain's outbound port on top of Spring Data
 * JPA. This is the only place in the codebase allowed to know that orders
 * are stored in Postgres via Hibernate — mirrors identity-service's
 * AccountRepositoryAdapter (class-level read-only transaction, explicit
 * `@Transactional` override on the one write method, `saveAndFlush` +
 * catching the unique-constraint violation for the same reason
 * `EmailAlreadyRegisteredException` does there).
 */
@Repository
@Transactional(readOnly = true)
class OrderRepositoryAdapter(
    private val jpaRepository: SpringDataOrderJpaRepository,
    private val entityManager: EntityManager,
) : OrderRepositoryPort {

    /**
     * `saveAndFlush` (not `save`) forces the INSERT — and therefore the
     * `idempotency_key` unique-constraint check — to happen inside this
     * try/catch, exactly like AccountRepositoryAdapter.save does for email
     * uniqueness. Returning normally from this method means the
     * transaction Spring opened for it has already committed — see
     * application.OrderService.placeOrder's kdoc for why that's what lets
     * the RabbitMQ publish happen "after commit" without needing a
     * separate `@TransactionalEventListener`.
     */
    @Transactional
    override fun insert(order: Order): Order {
        // Reference-only load (no SELECT) — status_id is fixed, seeded reference data
        // (V2__seed_order_statuses.sql), so a proxy is enough to satisfy the FK.
        val statusReference = entityManager.getReference(OrderStatusJpaEntity::class.java, order.status.dbId)

        val orderEntity = OrderJpaEntity(
            id = order.id,
            customerId = order.customerId,
            status = statusReference,
            totalAmount = order.totalAmount,
            idempotencyKey = order.idempotencyKey,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
        orderEntity.items = order.items.map { item ->
            OrderItemJpaEntity(
                id = item.id,
                order = orderEntity,
                productId = item.productId,
                productNameSnapshot = item.productNameSnapshot,
                unitPriceSnapshot = item.unitPriceSnapshot,
                quantity = item.quantity,
            )
        }.toMutableList()

        return try {
            jpaRepository.saveAndFlush(orderEntity).toDomain()
        } catch (constraintViolation: DataIntegrityViolationException) {
            throw DuplicateIdempotencyKeyException(order.idempotencyKey)
        }
    }

    override fun findById(id: UUID): Order? = jpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByIdempotencyKey(idempotencyKey: String): Order? =
        jpaRepository.findByIdempotencyKey(idempotencyKey)?.toDomain()

    override fun findSummaries(customerId: UUID?): List<OrderSummary> {
        val entities = if (customerId != null) {
            jpaRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        } else {
            jpaRepository.findAllByOrderByCreatedAtDesc()
        }
        return entities.map { it.toDomainSummary() }
    }

    /**
     * Loads the managed entity by id and mutates only `status`/`updatedAt`
     * (Phase-6's PLACED -> CANCELLED transition) — mirrors identity-service's
     * AccountRepositoryAdapter.update exactly (reference-swap for the status
     * FK, `saveAndFlush`). Never touches `items`, `totalAmount`,
     * `customerId`, or `idempotencyKey`.
     */
    @Transactional
    override fun update(order: Order): Order {
        val entity = jpaRepository.findById(order.id).orElseThrow {
            IllegalStateException("Cannot update order ${order.id}: no matching row found")
        }
        entity.status = entityManager.getReference(OrderStatusJpaEntity::class.java, order.status.dbId)
        entity.updatedAt = order.updatedAt
        return jpaRepository.saveAndFlush(entity).toDomain()
    }
}
