package com.minimart.notification.application

import com.minimart.notification.application.dto.ListNotificationsCommand
import com.minimart.notification.application.dto.RecordNotificationCommand
import com.minimart.notification.domain.model.Notification

/**
 * Inbound port (use-case boundary) for Phase-7's notification recording
 * and history endpoints. Both the RabbitMQ consumer and the web layer
 * (NotificationController) depend on this interface, not on the concrete
 * NotificationService — the messaging adapter is, architecturally, just
 * another driving adapter, the same role a controller plays.
 */
interface NotificationUseCase {

    /** NTF-001/NTF-002: records a notification for an order-placed/cancelled event. */
    fun recordNotification(command: RecordNotificationCommand): Notification

    /**
     * NTF-003: a Customer's own notification history, or — for an
     * Administrator passing `?accountId=` — any one account's history
     * (support lookup), or every account's notifications if an
     * Administrator omits it.
     *
     * @throws com.minimart.notification.domain.exception.ForbiddenActionException
     *   if a non-admin caller passes `?accountId=` for anyone but themselves.
     */
    fun listNotifications(command: ListNotificationsCommand): List<Notification>
}
