package com.minimart.order.application.dto

import java.util.UUID

/** POST /api/orders (scenarios 16/17/18). */
data class PlaceOrderCommand(
    val customerId: UUID,
    val idempotencyKey: String,
    val items: List<PlaceOrderLineItem>,
)

data class PlaceOrderLineItem(val productId: UUID, val quantity: Int)
