package com.minimart.notification.application.testsupport

import com.minimart.notification.domain.model.Notification
import com.minimart.notification.domain.port.NotificationRepositoryPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for NotificationRepositoryPort. Mirrors identity-service's FakeAccountRepository style. */
class FakeNotificationRepository : NotificationRepositoryPort {

    private val notificationsById = ConcurrentHashMap<UUID, Notification>()

    override fun insert(notification: Notification): Notification {
        notificationsById[notification.id] = notification
        return notification
    }

    override fun search(accountId: UUID?): List<Notification> =
        notificationsById.values
            .filter { accountId == null || it.accountId == accountId }
            .sortedByDescending { it.createdAt }
}
