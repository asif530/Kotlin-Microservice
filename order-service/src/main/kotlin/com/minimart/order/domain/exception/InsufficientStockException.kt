package com.minimart.order.domain.exception

import java.util.UUID

/** One line item's reservation failure, as shown in the Phase-5 doc's 409 `details` array. */
data class StockFailureDetail(val productId: UUID, val requested: Int, val available: Int)

/**
 * Raised by POST /api/orders when one or more line items could not be
 * reserved in the requested quantity (ORD-007/GEN-001). Per the Phase-5
 * doc: the entire order is rejected, and any reservations that DID succeed
 * for other line items in the same request are released again before this
 * is thrown (see application.OrderService — the compensating action
 * happens there, not here; this exception just carries the failure
 * details for the 409 response body).
 *
 * [details] includes every failed line item, not just the first — a
 * judgment call, not shown by the doc's own (single-item) example, made so
 * a customer with multiple problem items learns about all of them from one
 * response instead of retrying repeatedly to discover each one.
 */
class InsufficientStockException(val details: List<StockFailureDetail>) :
    RuntimeException("One or more items could not be reserved in the requested quantity.")
