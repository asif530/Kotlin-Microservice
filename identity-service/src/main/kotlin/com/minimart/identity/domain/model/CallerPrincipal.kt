package com.minimart.identity.domain.model

import java.util.UUID

/**
 * The authenticated caller resolved from a verified access token (see
 * domain.port.TokenVerifier) — account id (`sub` claim) and role (`role`
 * claim), the two facts every Phase-2 authorization decision needs. Plain
 * domain data, deliberately independent of how the token was transported or
 * verified (HTTP header, Servlet Filter, JWT library — all infrastructure).
 */
data class CallerPrincipal(
    val accountId: UUID,
    val role: RoleCode,
)
