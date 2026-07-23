package com.minimart.notification.infrastructure.persistence

import com.minimart.notification.domain.model.Notification
import com.minimart.notification.domain.model.NotificationType
import java.util.UUID

/** Maps the domain Notification to/from its Mongo document. Kept out of the domain/application layers on purpose. */

fun NotificationDocument.toDomain(): Notification = Notification(
    id = UUID.fromString(id),
    accountId = UUID.fromString(accountId),
    orderId = UUID.fromString(orderId),
    type = NotificationType.valueOf(type),
    message = message,
    createdAt = createdAt,
)

fun Notification.toDocument(): NotificationDocument = NotificationDocument(
    id = id.toString(),
    accountId = accountId.toString(),
    orderId = orderId.toString(),
    type = type.name,
    message = message,
    createdAt = createdAt,
)
