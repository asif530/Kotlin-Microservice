package com.minimart.notification.infrastructure.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares notification-service's half of ARCHITECTURE.md §5's RabbitMQ
 * topology — the consumer side of the same `order.events` topic exchange
 * order-service's own RabbitMqConfig declares. Redeclaring an exchange
 * with identical properties (name/type/durable/auto-delete) from a
 * different service is not "shared code" — it's each service
 * independently stating its own view of a topology it depends on, exactly
 * as idiomatic in Spring AMQP/RabbitMQ (redeclaration is idempotent as
 * long as the properties agree); the two services still share no JAR, no
 * database, and no Kotlin classes.
 *
 * Queue name (`notification.order-placed`) and the dead-letter-exchange
 * resilience mechanism are both fixed by ARCHITECTURE.md §5 and the
 * Phase-7 doc's own header. One queue is bound to *both* routing keys
 * (`order.placed`, `order.cancelled`) — the doc's own header names a
 * single queue for both event types, and OrderEventListener uses the
 * received routing key to tell them apart (see that class's kdoc).
 */
@Configuration
class RabbitMqConsumerConfig {

    @Bean
    fun orderEventsExchange(): TopicExchange = TopicExchange(EXCHANGE_NAME, true, false)

    /**
     * NTF-005 / scenario 28: a message that ultimately fails processing
     * (Mongo unreachable, whatever the cause) must land here rather than
     * being retried forever or silently dropped — see OrderEventListener's
     * manual-ack failure path for where a message actually ends up on this
     * exchange (queue-level `x-dead-letter-exchange`, triggered by a
     * `basicNack` with `requeue = false`).
     */
    @Bean
    fun deadLetterExchange(): DirectExchange = DirectExchange(DEAD_LETTER_EXCHANGE_NAME, true, false)

    @Bean
    fun deadLetterQueue(): Queue = QueueBuilder.durable(DEAD_LETTER_QUEUE_NAME).build()

    @Bean
    fun deadLetterBinding(deadLetterQueue: Queue, deadLetterExchange: DirectExchange): Binding =
        BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_QUEUE_NAME)

    @Bean
    fun orderEventsQueue(): Queue = QueueBuilder.durable(QUEUE_NAME)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE_NAME)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE_NAME)
        .build()

    @Bean
    fun orderPlacedBinding(orderEventsQueue: Queue, orderEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(orderEventsQueue).to(orderEventsExchange).with(ORDER_PLACED_ROUTING_KEY)

    @Bean
    fun orderCancelledBinding(orderEventsQueue: Queue, orderEventsExchange: TopicExchange): Binding =
        BindingBuilder.bind(orderEventsQueue).to(orderEventsExchange).with(ORDER_CANCELLED_ROUTING_KEY)

    /**
     * JSON — order-service publishes with the equivalent converter (see
     * that service's own RabbitMqConfig for why `JacksonJsonMessageConverter`
     * specifically, not the older/deprecated `Jackson2...` one, given
     * Spring Boot 4.0.6's Jackson-3-by-default autoconfiguration). Spring
     * Boot applies any single `MessageConverter` bean present to both
     * `RabbitTemplate` and the default `SimpleRabbitListenerContainerFactory`
     * automatically — no separate listener-container-factory bean needed
     * just for this.
     */
    @Bean
    fun messageConverter(): MessageConverter = JacksonJsonMessageConverter()

    companion object {
        const val EXCHANGE_NAME = "order.events"
        const val QUEUE_NAME = "notification.order-placed"
        const val DEAD_LETTER_EXCHANGE_NAME = "notification.order-placed.dlx"
        const val DEAD_LETTER_QUEUE_NAME = "notification.order-placed.dlq"
        const val ORDER_PLACED_ROUTING_KEY = "order.placed"
        const val ORDER_CANCELLED_ROUTING_KEY = "order.cancelled"
    }
}
