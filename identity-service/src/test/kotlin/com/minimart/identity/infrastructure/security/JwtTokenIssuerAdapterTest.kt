package com.minimart.identity.infrastructure.security

import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.UUID

class JwtTokenIssuerAdapterTest {

    @Test
    fun `issues an RS256 JWT carrying account id and role, verifiable with the exported public key`(@TempDir tempDir: File) {
        val properties = JwtProperties(
            issuer = "identity-service",
            expirationSeconds = 3600,
            keySize = 2048,
            privateKeyPath = File(tempDir, "private.pem").path,
            publicKeyExportPath = File(tempDir, "public.pem").path,
        )
        val keyPairProvider = RsaKeyPairProvider(properties)
        keyPairProvider.initializeKeyPair()
        val issuer = JwtTokenIssuerAdapter(keyPairProvider, properties)

        val now = Instant.now()
        val account = Account(
            id = UUID.randomUUID(),
            email = "alice.nguyen@example.test",
            passwordHash = "irrelevant-for-this-test",
            fullName = "Alice Nguyen",
            role = RoleCode.CUSTOMER,
            status = AccountStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )

        val issuedToken = issuer.issue(account)

        assertEquals("Bearer", issuedToken.tokenType)
        assertEquals(3600L, issuedToken.expiresInSeconds)
        assertTrue(issuedToken.token.startsWith("eyJ"), "should be a compact JWS with a JSON header")

        val claims = Jwts.parser()
            .verifyWith(keyPairProvider.publicKey)
            .build()
            .parseSignedClaims(issuedToken.token)
            .payload

        assertEquals(account.id.toString(), claims.subject)
        assertEquals("identity-service", claims.issuer)
        assertEquals("CUSTOMER", claims["role"])
        assertTrue(claims.expiration.after(claims.issuedAt))
    }
}
