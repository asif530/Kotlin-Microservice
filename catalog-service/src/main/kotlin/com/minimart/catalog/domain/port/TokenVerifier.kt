package com.minimart.catalog.domain.port

import com.minimart.catalog.domain.model.CallerPrincipal

/**
 * Outbound port for verifying an access token identity-service issued and
 * resolving the caller's identity from its claims. Implemented by
 * infrastructure.security.JwtTokenVerifierAdapter.
 *
 * Per the Phase-3 doc and the Gateway implementation guide ("Each service
 * must check the forwarded role claim itself for every (admin only)
 * endpoint"): Kong's jwt plugin verifies signature/expiry only, never
 * role-based authorization, and forwards the original Authorization header
 * through untouched rather than pre-decoded claims. catalog-service
 * therefore independently re-verifies every token itself for its own
 * admin-only endpoint (CAT-006), the same way identity-service does for its
 * Phase-2 endpoints.
 */
interface TokenVerifier {

    /**
     * @throws com.minimart.catalog.domain.exception.UnauthenticatedException
     *   if [token] is missing, malformed, expired, fails RS256 signature
     *   verification, or carries a `sub`/`role` claim that cannot be
     *   resolved to a valid account id / known role.
     */
    fun verify(token: String): CallerPrincipal
}
