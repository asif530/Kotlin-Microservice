package com.minimart.order.infrastructure.messaging

import com.minimart.order.domain.model.Order
import com.minimart.order.domain.port.OrderEventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

/**
 * Publishes `order.placed`/`order.cancelled` to the `order.events` topic
 * exchange (ARCHITECTURE.md §5). Guarantees the "must never throw past the
 * caller" contract domain.port.OrderEventPublisherPort's kdoc documents by
 * catching broadly here, at the one place that actually talks to
 * RabbitMQ — a broker outage becomes a logged error, never a failed
 * checkout/cancellation response for an order that's already committed to
 * Postgres.
 */
@Component
class RabbitMqOrderEventPublisherAdapter(
    private val rabbitTemplate: RabbitTemplate,
) : OrderEventPublisherPort {

    private val logger = LoggerFactory.getLogger(RabbitMqOrderEventPublisherAdapter::class.java)

    override fun publishOrderPlaced(order: Order) {
        val event = OrderPlacedEvent(
            orderId = order.id,
            customerId = order.customerId,
            totalAmount = order.totalAmount,
            placedAt = order.createdAt,
        )
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.ORDER_PLACED_ROUTING_KEY, event)
        } catch (amqpFailure: AmqpException) {
            logger.error("Failed to publish order.placed for order id={} — order is still PLACED in Postgres; notification will be missed", order.id, amqpFailure)
        }
    }

    override fun publishOrderCancelled(order: Order) {
        val event = OrderCancelledEvent(
            orderId = order.id,
            customerId = order.customerId,
            cancelledAt = order.updatedAt,
        )
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, RabbitMqConfig.ORDER_CANCELLED_ROUTING_KEY, event)
        } catch (amqpFailure: AmqpException) {
            logger.error("Failed to publish order.cancelled for order id={} — order is still CANCELLED in Postgres; notification will be missed", order.id, amqpFailure)
        }
    }
}
