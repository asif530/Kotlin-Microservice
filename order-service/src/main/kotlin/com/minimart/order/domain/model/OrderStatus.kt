package com.minimart.order.domain.model

/**
 * ORD-010: an order has exactly one status at all times, drawn from this
 * fixed list. [dbId]/[dbCode] mirror the already-seeded `order_status`
 * lookup table (V2__seed_order_statuses.sql) exactly. Only PLACED and
 * CANCELLED are ever actually assigned by any process in this system's
 * current scope — CONFIRMED/SHIPPED/DELIVERED are reserved names for
 * future capabilities (payment, fulfillment) that don't exist here yet.
 */
enum class OrderStatus(val dbId: Short, val dbCode: String) {
    PLACED(1, "PLACED"),
    CONFIRMED(2, "CONFIRMED"),
    SHIPPED(3, "SHIPPED"),
    DELIVERED(4, "DELIVERED"),
    CANCELLED(5, "CANCELLED"),
    ;

    companion object {
        fun fromDbCode(code: String): OrderStatus =
            entries.firstOrNull { it.dbCode == code }
                ?: throw IllegalStateException("Unknown order status code read from database: '$code'")
    }
}
