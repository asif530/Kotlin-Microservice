package com.minimart.notification.infrastructure.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document shape for the `notifications` collection — verbatim
 * from Archive/Development/Database §4.1 /
 * Archive/Development/Database-Dev/mongo/00_notifications_schema.js.
 * `_id` is a client-generated UUID string, not an ObjectId — mirrors
 * catalog-service's ProductDocument.
 */
@Document(collection = "notifications")
data class NotificationDocument(
    @Id
    val id: String,
    val accountId: String,
    val orderId: String,
    val type: String,
    val message: String,
    val createdAt: Instant,
)
