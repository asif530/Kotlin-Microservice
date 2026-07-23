package com.minimart.notification.web.dto

/** Matches the Phase-7 GET /api/notifications 200 response shape exactly. */
data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val total: Int,
)

data class NotificationResponse(
    val id: String,
    val orderId: String,
    val type: String,
    val message: String,
    val createdAt: String,
)
