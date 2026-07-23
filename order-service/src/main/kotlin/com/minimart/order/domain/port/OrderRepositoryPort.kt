package com.minimart.order.domain.port

import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderSummary
import java.util.UUID

/**
 * Outbound port (Clean Architecture / dependency inversion): the
 * application layer depends on this interface only, never on Spring Data
 * JPA directly. Implemented by infrastructure.persistence.OrderRepositoryAdapter.
 */
interface OrderRepositoryPort {

    /**
     * Persists a new order and its line items in one transaction (ORD-002:
     * always at least one item; enforced by the caller, not this port).
     *
     * @throws com.minimart.order.domain.exception.DuplicateIdempotencyKeyException
     *   if the database's unique constraint on `idempotency_key` is
     *   violated — this is the authoritative retry-safety guard (ORD-009),
     *   not the Redis fast-path cache (see domain.port.IdempotencyPort).
     */
    fun insert(order: Order): Order

    /** Full lookup by primary key, including line items. Used by GET /api/orders/{id}. */
    fun findById(id: UUID): Order?

    /** Used by the idempotent-retry fallback path when Redis's cache missed or expired (ORD-009). */
    fun findByIdempotencyKey(idempotencyKey: String): Order?

    /**
     * Order history, newest first (ORD-013) — deliberately returns
     * [OrderSummary], not [Order]: see that class's kdoc for why. Passing
     * `null` for [customerId] returns every customer's orders (an
     * Administrator's unfiltered support view); a non-null value narrows
     * to that one customer.
     */
    fun findSummaries(customerId: UUID?): List<OrderSummary>

    /**
     * Persists a status change (Phase-6's only mutation: PLACED ->
     * CANCELLED, ORD-011) — never touches `customerId`, `totalAmount`,
     * `idempotencyKey`, or any line item, all of which are immutable once
     * an order is placed (ORD-005/ORD-014).
     */
    fun update(order: Order): Order
}
