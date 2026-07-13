package com.minimart.identity.infrastructure.security

import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.port.IssuedToken
import com.minimart.identity.domain.port.TokenIssuer
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date

/**
 * Signs an RS256 JWT on successful login using [RsaKeyPairProvider]'s
 * private key. Claims are deliberately minimal — just what Phase-1 and
 * ARCHITECTURE.md §7 actually ask for (account id as `sub`, and `role` for
 * Kong's forwarded-role-claim admin gate) rather than guessing at a richer
 * contract nothing in the source documents specifies.
 */
@Component
class JwtTokenIssuerAdapter(
    private val rsaKeyPairProvider: RsaKeyPairProvider,
    private val jwtProperties: JwtProperties,
) : TokenIssuer {

    override fun issue(account: Account): IssuedToken {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plusSeconds(jwtProperties.expirationSeconds)

        val token = Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject(account.id.toString())
            .claim(ROLE_CLAIM, account.role.dbCode)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(rsaKeyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        return IssuedToken(
            token = token,
            tokenType = TOKEN_TYPE,
            expiresInSeconds = jwtProperties.expirationSeconds,
        )
    }

    private companion object {
        const val ROLE_CLAIM = "role"
        const val TOKEN_TYPE = "Bearer"
    }
}
