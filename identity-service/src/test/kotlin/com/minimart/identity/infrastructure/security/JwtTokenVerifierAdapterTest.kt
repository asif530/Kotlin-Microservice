package com.minimart.identity.infrastructure.security

import com.minimart.identity.domain.exception.UnauthenticatedException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtTokenVerifierAdapterTest {

    private fun newKeyPairProvider(tempDir: File): RsaKeyPairProvider {
        val properties = JwtProperties(
            issuer = "identity-service",
            expirationSeconds = 3600,
            keySize = 2048,
            privateKeyPath = File(tempDir, "private.pem").path,
            publicKeyExportPath = File(tempDir, "public.pem").path,
        )
        return RsaKeyPairProvider(properties).apply { initializeKeyPair() }
    }

    @Test
    fun `verify round-trips a token issued by JwtTokenIssuerAdapter back to the same account id and role`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val properties = JwtProperties("identity-service", 3600, 2048, "unused", "unused")
        val issuer = JwtTokenIssuerAdapter(keyPairProvider, properties)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val now = Instant.now()
        val account = Account(
            id = UUID.randomUUID(),
            email = "alice@example.test",
            passwordHash = "irrelevant",
            fullName = "Alice",
            role = RoleCode.ADMIN,
            status = AccountStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        val issued = issuer.issue(account)

        val caller = verifier.verify(issued.token)

        assertEquals(account.id, caller.accountId)
        assertEquals(RoleCode.ADMIN, caller.role)
    }

    @Test
    fun `verify rejects an expired token`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val expiredToken = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(keyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(expiredToken) }
    }

    @Test
    fun `verify rejects a token signed by a different keypair`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val impostorKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val tokenSignedByAnotherKey = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(impostorKeyPair.private, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(tokenSignedByAnotherKey) }
    }

    @Test
    fun `verify rejects a malformed, non-JWT-shaped string`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        assertThrows(UnauthenticatedException::class.java) { verifier.verify("not-a-jwt-at-all") }
    }

    @Test
    fun `verify rejects a blank token`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        assertThrows(UnauthenticatedException::class.java) { verifier.verify("") }
    }

    @Test
    fun `verify rejects a token whose sub claim is not a UUID`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject("not-a-uuid")
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(keyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }

    @Test
    fun `verify rejects a token with no role claim`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(keyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }

    @Test
    fun `verify rejects a token with an unrecognized role claim`(@TempDir tempDir: File) {
        val keyPairProvider = newKeyPairProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(keyPairProvider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "SUPERUSER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(keyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }
}
