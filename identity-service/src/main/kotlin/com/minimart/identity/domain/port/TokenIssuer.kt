package com.minimart.identity.domain.port

import com.minimart.identity.domain.model.Account

/**
 * A successfully issued access token, shaped to match the Phase-1
 * accessToken/tokenType/expiresIn response fields exactly.
 */
data class IssuedToken(
    val token: String,
    val tokenType: String,
    val expiresInSeconds: Long,
)

/**
 * Outbound port for signing an access token on successful login. Per
 * ARCHITECTURE.md §4/§7, identity-service holds an RSA keypair and signs an
 * RS256 JWT with the private key; Kong verifies it downstream with the
 * public key. Implemented by infrastructure.security.JwtTokenIssuerAdapter.
 */
interface TokenIssuer {
    fun issue(account: Account): IssuedToken
}
