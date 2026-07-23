package com.minimart.notification.infrastructure.persistence

import com.minimart.notification.domain.model.Notification
import com.minimart.notification.domain.port.NotificationRepositoryPort
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adapter implementing the domain's outbound port on top of Spring Data
 * MongoDB. This is the only place in the codebase allowed to know that
 * notifications are stored in Mongo's `notifications` collection —
 * mirrors catalog-service's ProductRepositoryAdapter.
 */
@Repository
class NotificationRepositoryAdapter(
    private val mongoRepository: SpringDataNotificationMongoRepository,
) : NotificationRepositoryPort {

    override fun insert(notification: Notification): Notification = mongoRepository.insert(notification.toDocument()).toDomain()

    override fun search(accountId: UUID?): List<Notification> {
        val documents = if (accountId != null) {
            mongoRepository.findByAccountIdOrderByCreatedAtDesc(accountId.toString())
        } else {
            mongoRepository.findAllByOrderByCreatedAtDesc()
        }
        return documents.map { it.toDomain() }
    }
}
