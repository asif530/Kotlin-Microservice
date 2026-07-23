package com.minimart.order.domain.exception

import com.minimart.order.domain.model.OrderStatus
import java.util.UUID

/**
 * Raised by POST /api/orders/{id}/cancel when the order is not in Placed
 * status — ORD-011: "Cancelling an order that is already Cancelled... is
 * rejected." ORD-010 means CANCELLED is the only other status reachable in
 * this system's current scope, but the message is built from the order's
 * actual current status rather than the literal string "CANCELLED", so it
 * stays accurate if that ever changes. Message matches the Phase-6 doc's
 * 409 response body verbatim for the CANCELLED case.
 */
class OrderNotCancellableException(val orderId: UUID, val currentStatus: OrderStatus) :
    RuntimeException("This order is ${currentStatus.dbCode} and cannot be cancelled again.")
