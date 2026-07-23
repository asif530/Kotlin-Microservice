package com.minimart.order.application.testsupport

import com.minimart.order.domain.exception.DuplicateIdempotencyKeyException
import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderSummary
import com.minimart.order.domain.port.OrderRepositoryPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for OrderRepositoryPort. Mirrors identity-service's FakeAccountRepository style. */
class FakeOrderRepository : OrderRepositoryPort {

    private val ordersById = ConcurrentHashMap<UUID, Order>()
    private val orderIdsByIdempotencyKey = ConcurrentHashMap<String, UUID>()

    /** Test hook to simulate a duplicate idempotency key slipping past the pre-check (a race). */
    var forceRaceOnNextInsert: Boolean = false

    override fun insert(order: Order): Order {
        if (forceRaceOnNextInsert || orderIdsByIdempotencyKey.containsKey(order.idempotencyKey)) {
            forceRaceOnNextInsert = false
            throw DuplicateIdempotencyKeyException(order.idempotencyKey)
        }
        ordersById[order.id] = order
        orderIdsByIdempotencyKey[order.idempotencyKey] = order.id
        return order
    }

    override fun findById(id: UUID): Order? = ordersById[id]

    override fun findByIdempotencyKey(idempotencyKey: String): Order? =
        orderIdsByIdempotencyKey[idempotencyKey]?.let { ordersById[it] }

    override fun findSummaries(customerId: UUID?): List<OrderSummary> =
        ordersById.values
            .filter { customerId == null || it.customerId == customerId }
            .sortedByDescending { it.createdAt }
            .map { OrderSummary(it.id, it.status, it.totalAmount, it.createdAt) }

    override fun update(order: Order): Order {
        ordersById[order.id] = order
        return order
    }
}
