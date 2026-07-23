package com.minimart.notification.infrastructure.messaging

import com.minimart.notification.application.NotificationUseCase
import com.minimart.notification.application.dto.RecordNotificationCommand
import com.minimart.notification.domain.model.NotificationType
import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * Consumes `order.placed`/`order.cancelled` off the
 * `notification.order-placed` queue (NTF-001/NTF-002) — the one queue is
 * bound to both routing keys (see RabbitMqConsumerConfig), so the received
 * routing key, not the payload shape, is what tells this listener which
 * notification type to record.
 *
 * Manual acknowledgment, per ARCHITECTURE.md §5 ("manual ack and a
 * dead-letter exchange"): a successfully recorded notification acks the
 * message; any failure (Mongo unreachable, a malformed/unrecognized
 * message, whatever the cause — Phase-7 doc scenario 28 / NTF-005) nacks
 * without requeue, which is what actually routes the message to the dead
 * letter exchange the queue is configured with (`x-dead-letter-exchange`)
 * — rather than retrying forever inline (which would block this queue for
 * every subsequent message) or silently dropping it (which manual ack
 * without a DLQ would risk).
 *
 * This never reaches back into order-service in any way, deliberately: by
 * the time this listener sees the event, the order/cancellation it
 * describes is already committed and final in order-service's own
 * Postgres — a failure here can only ever affect this service's own
 * `notifications` collection, never unwind or retry the order itself
 * (NTF-005's "never undoes, blocks, or delays" — this class is the
 * concrete mechanism that makes that true, not just a claim).
 */
@Component
class OrderEventListener(
    private val notificationUseCase: NotificationUseCase,
) {

    private val logger = LoggerFactory.getLogger(OrderEventListener::class.java)

    @RabbitListener(queues = [RabbitMqConsumerConfig.QUEUE_NAME])
    fun onOrderEvent(
        @Payload message: OrderEventMessage,
        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) routingKey: String,
        channel: Channel,
        @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long,
    ) {
        try {
            val type = notificationTypeFor(routingKey)
            notificationUseCase.recordNotification(RecordNotificationCommand(message.accountId, message.orderId, type))
            channel.basicAck(deliveryTag, false)
        } catch (failure: Exception) {
            logger.error(
                "Failed to record notification for orderId={} routingKey={} — routing to dead-letter exchange",
                message.orderId,
                routingKey,
                failure,
            )
            channel.basicNack(deliveryTag, false, false)
        }
    }

    private fun notificationTypeFor(routingKey: String): NotificationType = when (routingKey) {
        RabbitMqConsumerConfig.ORDER_PLACED_ROUTING_KEY -> NotificationType.ORDER_PLACED
        RabbitMqConsumerConfig.ORDER_CANCELLED_ROUTING_KEY -> NotificationType.ORDER_CANCELLED
        else -> throw IllegalArgumentException("Unrecognized routing key for notification.order-placed queue: '$routingKey'")
    }
}
