package com.minimart.notification.infrastructure.messaging

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * notification-service's own view of order-service's `order.placed`/
 * `order.cancelled` payloads (order-service's OrderPlacedEvent/
 * OrderCancelledEvent) — deliberately a separate, independently-defined
 * class, not a shared one: per ARCHITECTURE.md §2/§11, the *only* thing
 * one service module is allowed to depend on from another is a generated
 * gRPC stub module, and plain RabbitMQ JSON payloads between order-service
 * and notification-service were never specified as that kind of contract
 * (the Phase-7 doc itself says "the exact payload is order-service's
 * contract to define, not notification-service's").
 *
 * Only `orderId`/`accountId` are read — NTF-001/NTF-002 don't need
 * `totalAmount`/`placedAt`/`cancelledAt`, and `@JsonIgnoreProperties`
 * lets this class safely ignore whichever of those extra fields shows up
 * without needing to know which event type it's deserializing (the
 * routing key, not the payload shape, is what actually distinguishes
 * order.placed from order.cancelled — see OrderEventListener).
 *
 * Note the field name: order-service's own payload calls this
 * `customerId`, not `accountId` — this class maps it to `accountId` at the
 * JSON boundary, since that's the vocabulary notification-service's own
 * domain (NTF-001..NTF-003) uses throughout.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderEventMessage(
    val orderId: UUID,
    val customerId: UUID,
) {
    val accountId: UUID get() = customerId
}
