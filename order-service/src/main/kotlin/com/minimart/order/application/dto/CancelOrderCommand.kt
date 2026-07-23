package com.minimart.order.application.dto

import java.util.UUID

/**
 * POST /api/orders/{id}/cancel (ORD-011, scenario 21/22). No role is
 * carried here, unlike GetOrderCommand/ListOrdersCommand — ORD-011 grants
 * cancellation only to "a Customer... their own order," with no
 * Administrator-override clause anywhere in BUSINESS_RULES.md (unlike
 * ORD-013's explicit admin exception for viewing). OrderService therefore
 * checks ownership alone, regardless of the caller's role.
 */
data class CancelOrderCommand(
    val callerId: UUID,
    val orderId: UUID,
)
