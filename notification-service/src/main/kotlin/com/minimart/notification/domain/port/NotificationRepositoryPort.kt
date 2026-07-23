package com.minimart.notification.domain.port

import com.minimart.notification.domain.model.Notification
import java.util.UUID

/**
 * Outbound port (Clean Architecture / dependency inversion): the
 * application layer depends on this interface only, never on Spring Data
 * MongoDB directly. Implemented by
 * infrastructure.persistence.NotificationRepositoryAdapter.
 */
interface NotificationRepositoryPort {

    /** Persists a newly recorded notification (NTF-001/NTF-002). */
    fun insert(notification: Notification): Notification

    /**
     * History, newest first (NTF-003). Passing `null` for [accountId]
     * returns every account's notifications (an Administrator's
     * unfiltered support view — mirrors order-service's
     * OrderRepositoryPort.findSummaries); a non-null value narrows to one
     * account.
     */
    fun search(accountId: UUID?): List<Notification>
}
