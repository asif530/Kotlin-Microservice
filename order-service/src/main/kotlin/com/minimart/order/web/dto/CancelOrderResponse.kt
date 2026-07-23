package com.minimart.order.web.dto

/** Matches the Phase-6 POST /api/orders/{id}/cancel 200 response shape exactly. */
data class CancelOrderResponse(
    val id: String,
    val status: String,
    val updatedAt: String,
)
