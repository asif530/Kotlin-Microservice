package com.minimart.order.domain.port

import com.minimart.order.domain.model.Order

/**
 * Outbound port for publishing order domain events (ARCHITECTURE.md §5) —
 * implemented by infrastructure.messaging.RabbitMqOrderEventPublisherAdapter.
 * Phase 7 (notification-service) is the eventual consumer of both events
 * this port can publish; this phase only needs to be a correct,
 * best-effort producer.
 */
interface OrderEventPublisherPort {

    /**
     * Publishes after [order]'s Postgres row is already committed (see
     * application.OrderService's call site for how "after commit" is
     * achieved here). Must never throw past the caller — a broker outage
     * must not turn an already-successfully-placed order into a failed
     * checkout response; ARCHITECTURE.md §13 explicitly defers the
     * transactional-outbox hardening that would close this small gap.
     */
    fun publishOrderPlaced(order: Order)

    /**
     * Publishes `order.cancelled` (Phase 6) after [order]'s status change
     * to CANCELLED is already committed — same "after commit" and
     * "never throws" contract as [publishOrderPlaced], for the same reason.
     */
    fun publishOrderCancelled(order: Order)
}
