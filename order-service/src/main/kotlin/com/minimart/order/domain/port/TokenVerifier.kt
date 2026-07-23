package com.minimart.order.domain.port

import com.minimart.order.domain.model.CallerPrincipal

/**
 * Outbound port for verifying an access token identity-service issued and
 * resolving the caller's identity from its claims. Implemented by
 * infrastructure.security.JwtTokenVerifierAdapter — mirrors
 * catalog-service's own TokenVerifier exactly (see that interface's kdoc
 * for the full "why does every service re-verify independently" reasoning:
 * Kong verifies signature/expiry only, never role-based authorization).
 */
interface TokenVerifier {

    /**
     * @throws com.minimart.order.domain.exception.UnauthenticatedException
     *   if [token] is missing, malformed, expired, fails RS256 signature
     *   verification, or carries a `sub`/`role` claim that cannot be
     *   resolved to a valid account id / known role.
     */
    fun verify(token: String): CallerPrincipal
}
