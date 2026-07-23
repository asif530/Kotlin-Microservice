package com.minimart.order.infrastructure.messaging

import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares order-service's half of ARCHITECTURE.md §5's RabbitMQ topology —
 * the `order.events` topic exchange, durable so it survives a broker
 * restart. order-service is the producer; it does not declare
 * notification-service's `notification.order-placed` queue or binding —
 * that's Phase 7's own concern to declare when it exists, mirroring how
 * catalog-service in Phase 3/4 never had to know anything about
 * order-service's not-yet-existing schema. Declaring the exchange here
 * (rather than assuming Phase 7 will always run first) means
 * `RabbitTemplate.convertAndSend` succeeds — a queue-less exchange simply
 * drops the message if no consumer has bound anything yet, rather than
 * order-service's publish failing outright.
 */
@Configuration
class RabbitMqConfig {

    @Bean
    fun orderEventsExchange(): TopicExchange = TopicExchange(EXCHANGE_NAME, true, false)

    /**
     * JSON, not Spring AMQP's default Java-serialization converter —
     * notification-service (a separate JVM process, and per
     * ARCHITECTURE.md §2 possibly not even guaranteed to be Kotlin/JVM in
     * spirit even though it happens to be here) must be able to read this
     * payload without sharing order-service's classes.
     *
     * `JacksonJsonMessageConverter` (not the older `Jackson2JsonMessageConverter`,
     * which is deprecated in this Spring AMQP version) — confirmed by
     * actually wiring this up: Spring Boot 4.0.6 autoconfigures a Jackson
     * **3** (`tools.jackson.databind`) `ObjectMapper`/`JsonMapper` by
     * default, not the classic Jackson 2 (`com.fasterxml.jackson.databind`)
     * one `Jackson2JsonMessageConverter` needs — using that older converter
     * failed at context startup with `NoSuchBeanDefinitionException` for
     * `com.fasterxml.jackson.databind.ObjectMapper`, since no such bean
     * exists here. The no-arg constructor builds its own internal
     * `JsonMapper` (java.time/Kotlin support included), which is enough for
     * `OrderPlacedEvent`'s fields — not injecting Spring's own Jackson 3
     * bean explicitly since nothing else in this class needs to share its
     * exact configuration.
     */
    @Bean
    fun messageConverter(): MessageConverter = JacksonJsonMessageConverter()

    companion object {
        const val EXCHANGE_NAME = "order.events"
        const val ORDER_PLACED_ROUTING_KEY = "order.placed"
        const val ORDER_CANCELLED_ROUTING_KEY = "order.cancelled"
    }
}
