package com.minimart.order.infrastructure.messaging

import java.time.Instant
import java.util.UUID

/**
 * The `order.cancelled` message payload published to RabbitMQ. Same
 * judgment-call status as OrderPlacedEvent — Phase 7 doesn't exist yet and
 * no source document fixes this shape.
 */
data class OrderCancelledEvent(
    val orderId: UUID,
    val customerId: UUID,
    val cancelledAt: Instant,
)
