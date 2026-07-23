package com.minimart.notification.domain.exception

/**
 * Raised when GET /api/notifications is called with a missing, malformed,
 * expired, or otherwise invalid access token. Mirrors catalog-service/
 * order-service's own UnauthenticatedException — same "no detail beyond
 * UNAUTHORIZED" posture.
 */
class UnauthenticatedException(message: String = "Authentication is required.") : RuntimeException(message)
