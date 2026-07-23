package com.minimart.order.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The shape GET /api/orders (list) actually needs — id/status/totalAmount/
 * createdAt, no line items. A deliberately separate, lighter read model
 * from [Order] rather than reusing it with `items` left empty: mapping
 * straight to this shape means the JPA adapter never touches an order's
 * lazy `orderItems` collection while building a list of many orders, which
 * is what keeps GET /api/orders from being an N+1 query as order history
 * grows (see infrastructure.persistence.OrderRepositoryAdapter).
 */
data class OrderSummary(
    val id: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val createdAt: Instant,
)
