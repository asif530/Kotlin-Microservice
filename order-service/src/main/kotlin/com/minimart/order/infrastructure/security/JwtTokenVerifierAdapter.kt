package com.minimart.order.infrastructure.security

import com.minimart.order.domain.exception.UnauthenticatedException
import com.minimart.order.domain.model.CallerPrincipal
import com.minimart.order.domain.model.RoleCode
import com.minimart.order.domain.port.TokenVerifier
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Verifies the RS256 JWTs identity-service issues, using
 * [PublicKeyProvider]'s public key — mirrors catalog-service's own
 * JwtTokenVerifierAdapter exactly (same claim shape: `sub`, `role`).
 */
@Component
class JwtTokenVerifierAdapter(
    private val publicKeyProvider: PublicKeyProvider,
) : TokenVerifier {

    private val logger = LoggerFactory.getLogger(JwtTokenVerifierAdapter::class.java)

    override fun verify(token: String): CallerPrincipal {
        val claims = try {
            Jwts.parser()
                .verifyWith(publicKeyProvider.publicKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (malformedOrExpiredOrUnsigned: JwtException) {
            logger.warn("Rejected access token: {}", malformedOrExpiredOrUnsigned.message)
            throw UnauthenticatedException()
        } catch (blankOrNotCompact: IllegalArgumentException) {
            logger.warn("Rejected access token: {}", blankOrNotCompact.message)
            throw UnauthenticatedException()
        }

        val accountId = try {
            UUID.fromString(claims.subject)
        } catch (notAUuid: IllegalArgumentException) {
            logger.warn("Rejected access token: 'sub' claim is not a valid account id")
            throw UnauthenticatedException()
        }

        val roleClaim = claims[ROLE_CLAIM] as? String
        if (roleClaim == null) {
            logger.warn("Rejected access token: missing '{}' claim", ROLE_CLAIM)
            throw UnauthenticatedException()
        }

        val role = try {
            RoleCode.fromDbCode(roleClaim)
        } catch (unknownRole: IllegalStateException) {
            logger.warn("Rejected access token: unrecognized role claim '{}'", roleClaim)
            throw UnauthenticatedException()
        }

        return CallerPrincipal(accountId = accountId, role = role)
    }

    private companion object {
        const val ROLE_CLAIM = "role"
    }
}
