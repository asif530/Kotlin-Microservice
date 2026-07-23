package com.minimart.order.web.dto

/** Matches the Phase-5 doc's POST /api/orders 201 and GET /api/orders/{id} 200 response shape exactly. */
data class OrderResponse(
    val id: String,
    val customerId: String,
    val status: String,
    val totalAmount: String,
    val items: List<OrderItemResponse>,
    val createdAt: String,
)

data class OrderItemResponse(
    val productId: String,
    val productName: String,
    val unitPrice: String,
    val quantity: Int,
    val lineTotal: String,
)
