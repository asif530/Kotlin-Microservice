package com.minimart.order.web

import com.minimart.order.application.OrderUseCase
import com.minimart.order.application.dto.CancelOrderCommand
import com.minimart.order.application.dto.GetOrderCommand
import com.minimart.order.application.dto.ListOrdersCommand
import com.minimart.order.application.dto.PlaceOrderCommand
import com.minimart.order.application.dto.PlaceOrderLineItem
import com.minimart.order.domain.model.CallerPrincipal
import com.minimart.order.web.dto.CancelOrderResponse
import com.minimart.order.web.dto.OrderListResponse
import com.minimart.order.web.dto.OrderResponse
import com.minimart.order.web.dto.PlaceOrderRequest
import com.minimart.order.web.dto.toCancelOrderResponse
import com.minimart.order.web.dto.toOrderResponse
import com.minimart.order.web.dto.toOrderSummaryResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Phase 5/Phase 6 — Order placement and cancellation. Implements the four
 * endpoints specified in
 * Archive/Development/Backend/Phase/Phase-5-Order-Placement and
 * Archive/Development/Backend/Phase/Phase-6-Order-Cancellation.
 *
 * Kong verifies the token's signature and expiry at the gateway
 * (kong.decl.yaml's `order-create`/`order-list`/`order-detail`/`order-cancel`
 * routes) but never role-based/ownership authorization; that's entirely
 * this service's own responsibility, enforced in OrderService
 * (ORD-001/ORD-011/ORD-013), not here — this controller only maps HTTP <->
 * the use-case boundary.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderUseCase: OrderUseCase,
) {

    private val logger = LoggerFactory.getLogger(OrderController::class.java)

    @PostMapping
    fun placeOrder(
        caller: CallerPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PlaceOrderRequest,
    ): ResponseEntity<OrderResponse> {
        logger.info("POST /api/orders idempotencyKey={}", idempotencyKey)
        val order = orderUseCase.placeOrder(
            PlaceOrderCommand(
                customerId = caller.accountId,
                idempotencyKey = idempotencyKey,
                items = request.items.map { PlaceOrderLineItem(it.productId, it.quantity) },
            ),
        )
        // Per the Phase-5 doc, an idempotent retry also returns 201 with the original body — not
        // 200 — so this status is unconditional regardless of whether this call created the order
        // or returned an already-existing one.
        return ResponseEntity.status(HttpStatus.CREATED).body(order.toOrderResponse())
    }

    @GetMapping("/{id}")
    fun getOrder(caller: CallerPrincipal, @PathVariable id: UUID): ResponseEntity<OrderResponse> {
        logger.info("GET /api/orders/{}", id)
        val order = orderUseCase.getOrder(GetOrderCommand(callerId = caller.accountId, callerRole = caller.role, orderId = id))
        return ResponseEntity.ok(order.toOrderResponse())
    }

    @GetMapping
    fun listOrders(caller: CallerPrincipal, @RequestParam(required = false) customerId: UUID?): ResponseEntity<OrderListResponse> {
        logger.info("GET /api/orders customerId={}", customerId)
        val summaries = orderUseCase.listOrders(
            ListOrdersCommand(callerId = caller.accountId, callerRole = caller.role, customerIdFilter = customerId),
        )
        val items = summaries.map { it.toOrderSummaryResponse() }
        return ResponseEntity.ok(OrderListResponse(items = items, total = items.size))
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(caller: CallerPrincipal, @PathVariable id: UUID): ResponseEntity<CancelOrderResponse> {
        logger.info("POST /api/orders/{}/cancel", id)
        val order = orderUseCase.cancelOrder(CancelOrderCommand(callerId = caller.accountId, orderId = id))
        return ResponseEntity.ok(order.toCancelOrderResponse())
    }
}
