package com.minimart.notification.application.dto

import com.minimart.notification.domain.model.NotificationType
import java.util.UUID

/**
 * Raised by the RabbitMQ consumer (NTF-001/NTF-002) when an `order.placed`
 * or `order.cancelled` event is received. No `message` field — the fixed,
 * type-derived text ("Your order has been placed."/"...cancelled.") is
 * NotificationService's business logic, not something the messaging
 * adapter should decide.
 */
data class RecordNotificationCommand(
    val accountId: UUID,
    val orderId: UUID,
    val type: NotificationType,
)
