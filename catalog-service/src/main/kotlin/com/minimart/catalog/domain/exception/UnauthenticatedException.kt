package com.minimart.catalog.domain.exception

/**
 * Raised when POST /api/products is called with a missing, malformed,
 * expired, or otherwise invalid access token. Not named by the Phase-3 doc
 * (its own examples only show the admin-token success path and the
 * Customer 403 case) — 401 UNAUTHORIZED is this implementation's own
 * judgment call, following the same precedent identity-service's own
 * UnauthenticatedException already established for Phase-2: no detail is
 * given about *why* verification failed, since distinguishing "missing
 * header" from "expired token" from "bad signature" to a caller has no
 * legitimate use and is a minor information leak about the verification
 * mechanism.
 */
class UnauthenticatedException(message: String = "Authentication is required.") : RuntimeException(message)
