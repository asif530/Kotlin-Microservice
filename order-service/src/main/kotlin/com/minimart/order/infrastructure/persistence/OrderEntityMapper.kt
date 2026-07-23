package com.minimart.order.infrastructure.persistence

import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderItem
import com.minimart.order.domain.model.OrderStatus
import com.minimart.order.domain.model.OrderSummary

/** Maps the JPA entity to the domain model. Kept out of the domain layer on purpose. */

/** Full mapping, including line items — touches the lazy `items` collection. Only use for a single order (GET /api/orders/{id}). */
fun OrderJpaEntity.toDomain(): Order = Order(
    id = id,
    customerId = customerId,
    status = OrderStatus.fromDbCode(status.code),
    totalAmount = totalAmount,
    idempotencyKey = idempotencyKey,
    items = items.map { it.toDomain() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Summary-only mapping — deliberately never touches `items`, so mapping a
 * list of many orders (GET /api/orders) never triggers the N+1 that
 * accessing each order's lazy line-item collection would (see
 * OrderJpaEntity kdoc).
 */
fun OrderJpaEntity.toDomainSummary(): OrderSummary = OrderSummary(
    id = id,
    status = OrderStatus.fromDbCode(status.code),
    totalAmount = totalAmount,
    createdAt = createdAt,
)

fun OrderItemJpaEntity.toDomain(): OrderItem = OrderItem(
    id = id,
    productId = productId,
    productNameSnapshot = productNameSnapshot,
    unitPriceSnapshot = unitPriceSnapshot,
    quantity = quantity,
)
