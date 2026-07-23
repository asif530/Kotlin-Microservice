package com.minimart.order.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The order-service aggregate root. [totalAmount] is the sum of every line
 * item's `lineTotal` (ORD-006, no taxes/shipping/discounts). [customerId]
 * never changes after placement (ORD-014), even if that account is later
 * Deactivated (ACC-007) — this is a plain Kotlin data class with no
 * persistence annotations, mirroring identity-service's Account /
 * catalog-service's Product.
 */
data class Order(
    val id: UUID,
    val customerId: UUID,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val idempotencyKey: String,
    val items: List<OrderItem>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
