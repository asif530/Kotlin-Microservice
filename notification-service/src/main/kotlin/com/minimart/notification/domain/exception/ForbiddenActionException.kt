package com.minimart.notification.domain.exception

/**
 * Raised when an authenticated caller is not permitted to perform the
 * requested action — NTF-003: a Customer passing `?accountId=` for anyone
 * but themselves on GET /api/notifications. [message] is supplied by the
 * call site verbatim, mirroring identity-service/catalog-service/
 * order-service's own ForbiddenActionException.
 */
class ForbiddenActionException(message: String) : RuntimeException(message)
