package com.minimart.notification.domain.model

import java.time.Instant
import java.util.UUID

/**
 * The notification-service aggregate root — a plain Kotlin data class with
 * no persistence annotations, mirroring identity-service's Account /
 * catalog-service's Product / order-service's Order. NTF-004: this is an
 * in-system record only, never a promise of outbound delivery.
 */
data class Notification(
    val id: UUID,
    val accountId: UUID,
    val orderId: UUID,
    val type: NotificationType,
    val message: String,
    val createdAt: Instant,
)
