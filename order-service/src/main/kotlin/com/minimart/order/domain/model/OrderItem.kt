package com.minimart.order.domain.model

import java.math.BigDecimal
import java.util.UUID

/**
 * A single line item on a placed order. [productNameSnapshot]/[unitPriceSnapshot]
 * are captured at placement time and never change afterward (ORD-005),
 * even if catalog-service's product record is later edited (CAT-010) or
 * deactivated (CAT-008) — this is what makes CAT-009 safe.
 *
 * [lineTotal] is computed here (unitPriceSnapshot × quantity, ORD-006)
 * rather than read back from Postgres's `line_total` generated column —
 * the two are always identical by construction (same formula), so this
 * domain model doesn't need to round-trip through the DB to know its own
 * derived value (see infrastructure.persistence.OrderItemJpaEntity kdoc).
 */
data class OrderItem(
    val id: UUID,
    val productId: UUID,
    val productNameSnapshot: String,
    val unitPriceSnapshot: BigDecimal,
    val quantity: Int,
) {
    val lineTotal: BigDecimal get() = unitPriceSnapshot.multiply(BigDecimal(quantity))
}
