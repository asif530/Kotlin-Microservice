package com.minimart.order.web.dto

/** Matches the Phase-5 doc's GET /api/orders 200 response shape exactly — no line items. */
data class OrderListResponse(
    val items: List<OrderSummaryResponse>,
    val total: Int,
)

data class OrderSummaryResponse(
    val id: String,
    val status: String,
    val totalAmount: String,
    val createdAt: String,
)
