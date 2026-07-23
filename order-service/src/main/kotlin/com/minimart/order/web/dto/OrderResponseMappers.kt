package com.minimart.order.web.dto

import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderItem
import com.minimart.order.domain.model.OrderSummary

/** Maps the domain model to Phase-5/Phase-6's response DTOs. Kept out of the domain/application layers on purpose. */

fun Order.toOrderResponse(): OrderResponse = OrderResponse(
    id = id.toString(),
    customerId = customerId.toString(),
    status = status.dbCode,
    totalAmount = totalAmount.toPlainString(),
    items = items.map { it.toOrderItemResponse() },
    createdAt = createdAt.toString(),
)

fun OrderItem.toOrderItemResponse(): OrderItemResponse = OrderItemResponse(
    productId = productId.toString(),
    productName = productNameSnapshot,
    unitPrice = unitPriceSnapshot.toPlainString(),
    quantity = quantity,
    lineTotal = lineTotal.toPlainString(),
)

fun OrderSummary.toOrderSummaryResponse(): OrderSummaryResponse = OrderSummaryResponse(
    id = id.toString(),
    status = status.dbCode,
    totalAmount = totalAmount.toPlainString(),
    createdAt = createdAt.toString(),
)

fun Order.toCancelOrderResponse(): CancelOrderResponse = CancelOrderResponse(
    id = id.toString(),
    status = status.dbCode,
    updatedAt = updatedAt.toString(),
)
