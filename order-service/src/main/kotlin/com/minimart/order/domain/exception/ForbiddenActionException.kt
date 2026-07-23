package com.minimart.order.domain.exception

/**
 * Raised when an authenticated caller is not permitted to perform the
 * requested action — ORD-013: a Customer passing `?customerId=` for
 * anyone but themselves on GET /api/orders. [message] is supplied by the
 * call site verbatim, mirroring identity-service/catalog-service's own
 * ForbiddenActionException.
 */
class ForbiddenActionException(message: String) : RuntimeException(message)
