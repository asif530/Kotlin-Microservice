package com.minimart.order.infrastructure.messaging

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The `order.placed` message payload published to RabbitMQ. Phase 7
 * (notification-service) doesn't exist yet, and no source document fixes
 * this shape — these are this implementation's own judgment call for
 * "the minimum a notification consumer would need to reference the order
 * and greet its buyer," not a wire contract Phase 7 is required to match
 * exactly; revisit once that phase actually specifies what it consumes.
 */
data class OrderPlacedEvent(
    val orderId: UUID,
    val customerId: UUID,
    val totalAmount: BigDecimal,
    val placedAt: Instant,
)
