package com.minimart.order.domain.exception

import java.util.UUID

/**
 * Raised by GET /api/orders/{id} when no order exists with the given id
 * *visible to this caller* — ORD-013: a Customer requesting another
 * customer's order id gets this exact same 404, not a 403, so the order's
 * existence isn't confirmed to someone who shouldn't see it (mirrors
 * CAT-008's "Deactivated looks like missing" posture already established
 * in catalog-service).
 */
class OrderNotFoundException(val orderId: UUID) : RuntimeException("No order with this id.")
