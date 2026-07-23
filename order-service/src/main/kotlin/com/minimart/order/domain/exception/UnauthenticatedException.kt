package com.minimart.order.domain.exception

/**
 * Raised when a protected /api/orders route is called with a missing,
 * malformed, expired, or otherwise invalid access token. Mirrors
 * catalog-service's own UnauthenticatedException — same "no detail beyond
 * UNAUTHORIZED" posture, same judgment call (not named by any Phase-5 doc
 * example).
 */
class UnauthenticatedException(message: String = "Authentication is required.") : RuntimeException(message)
