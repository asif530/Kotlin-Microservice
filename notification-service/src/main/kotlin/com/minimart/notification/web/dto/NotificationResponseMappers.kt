package com.minimart.notification.web.dto

import com.minimart.notification.domain.model.Notification

/** Maps the domain model to Phase-7's response DTO. Kept out of the domain/application layers on purpose. */
fun Notification.toNotificationResponse(): NotificationResponse = NotificationResponse(
    id = id.toString(),
    orderId = orderId.toString(),
    type = type.name,
    message = message,
    createdAt = createdAt.toString(),
)
