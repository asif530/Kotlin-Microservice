package com.minimart.identity.domain.port

import com.minimart.identity.domain.model.CallerPrincipal

/**
 * Outbound port for verifying a previously issued access token (see
 * [TokenIssuer]) and resolving the caller's identity from its claims.
 * Implemented by infrastructure.security.JwtTokenVerifierAdapter.
 *
 * Kong verifies signature and expiry at the gateway for the routes it
 * protects (ARCHITECTURE.md §7, kong.decl.yaml's `claims_to_verify: [exp]`),
 * but forwards the original Authorization header through untouched rather
 * than pre-decoded claims — and Kong's jwt plugin never enforces role-based
 * authorization at all, only identity (signature + expiry). identity-service
 * therefore independently re-verifies every token itself and is the sole
 * source of the caller's resolved id/role for its own authorization
 * decisions (ACC-008, ACC-009, ACC-011).
 */
interface TokenVerifier {

    /**
     * @throws com.minimart.identity.domain.exception.UnauthenticatedException
     *   if [token] is missing, malformed, expired, fails RS256 signature
     *   verification, or carries a `sub`/`role` claim that cannot be
     *   resolved to a real account id / known role.
     */
    fun verify(token: String): CallerPrincipal
}
