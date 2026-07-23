package com.minimart.notification.domain.model

/**
 * NTF-001/NTF-002: the two notification types this phase ever records —
 * mirrors Archive/Development/Database §4.1's `type` enum exactly.
 * NTF-006 (the reactivation-check notification) is a distinct, later type
 * this phase does not implement — Phase 7 covers only order.placed/
 * order.cancelled consumption (scenarios 24-28).
 */
enum class NotificationType {
    ORDER_PLACED,
    ORDER_CANCELLED,
}
