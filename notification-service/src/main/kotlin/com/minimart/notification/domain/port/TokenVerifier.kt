package com.minimart.notification.domain.port

import com.minimart.notification.domain.model.CallerPrincipal

/**
 * Outbound port for verifying an access token identity-service issued and
 * resolving the caller's identity from its claims. Implemented by
 * infrastructure.security.JwtTokenVerifierAdapter — mirrors
 * catalog-service/order-service's own TokenVerifier exactly (Kong verifies
 * signature/expiry only, never role-based authorization, so every service
 * re-verifies independently).
 */
interface TokenVerifier {

    /**
     * @throws com.minimart.notification.domain.exception.UnauthenticatedException
     *   if [token] is missing, malformed, expired, fails RS256 signature
     *   verification, or carries a `sub`/`role` claim that cannot be
     *   resolved to a valid account id / known role.
     */
    fun verify(token: String): CallerPrincipal
}
