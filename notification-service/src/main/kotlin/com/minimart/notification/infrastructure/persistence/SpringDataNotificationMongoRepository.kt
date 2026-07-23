package com.minimart.notification.infrastructure.persistence

import org.springframework.data.mongodb.repository.MongoRepository

interface SpringDataNotificationMongoRepository : MongoRepository<NotificationDocument, String> {

    fun findByAccountIdOrderByCreatedAtDesc(accountId: String): List<NotificationDocument>

    fun findAllByOrderByCreatedAtDesc(): List<NotificationDocument>
}
