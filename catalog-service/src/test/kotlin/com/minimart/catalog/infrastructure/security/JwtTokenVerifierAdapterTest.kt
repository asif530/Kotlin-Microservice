package com.minimart.catalog.infrastructure.security

import com.minimart.catalog.domain.exception.UnauthenticatedException
import com.minimart.catalog.domain.model.RoleCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Mirrors identity-service's own JwtTokenVerifierAdapterTest, adapted for a
 * verify-only adapter: this test generates its own RSA keypair, writes the
 * public half as a PEM [PublicKeyProvider] loads, and signs tokens directly
 * with the private half — catalog-service never issues tokens itself, so
 * there's no TokenIssuer counterpart to round-trip through.
 */
class JwtTokenVerifierAdapterTest {

    private fun newPublicKeyProvider(tempDir: File): Pair<PublicKeyProvider, PrivateKey> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val publicKeyFile = File(tempDir, "public.pem")
        val base64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val pem = buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            base64.chunked(64).forEach { line -> append(line).append('\n') }
            append("-----END PUBLIC KEY-----\n")
        }
        publicKeyFile.writeText(pem)

        val provider = PublicKeyProvider(JwtProperties(publicKeyPath = publicKeyFile.path)).apply { loadPublicKey() }
        return provider to keyPair.private
    }

    @Test
    fun `verify resolves the account id and role from a token signed by the matching private key`(@TempDir tempDir: File) {
        val (provider, privateKey) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)
        val accountId = UUID.randomUUID()

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject(accountId.toString())
            .claim("role", "ADMIN")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()

        val caller = verifier.verify(token)

        assertEquals(accountId, caller.accountId)
        assertEquals(RoleCode.ADMIN, caller.role)
    }

    @Test
    fun `verify rejects an expired token`(@TempDir tempDir: File) {
        val (provider, privateKey) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        val expiredToken = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(expiredToken) }
    }

    @Test
    fun `verify rejects a token signed by a different keypair`(@TempDir tempDir: File) {
        val (provider, _) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

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
        val (provider, _) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        assertThrows(UnauthenticatedException::class.java) { verifier.verify("not-a-jwt-at-all") }
    }

    @Test
    fun `verify rejects a blank token`(@TempDir tempDir: File) {
        val (provider, _) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        assertThrows(UnauthenticatedException::class.java) { verifier.verify("") }
    }

    @Test
    fun `verify rejects a token whose sub claim is not a UUID`(@TempDir tempDir: File) {
        val (provider, privateKey) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject("not-a-uuid")
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }

    @Test
    fun `verify rejects a token with no role claim`(@TempDir tempDir: File) {
        val (provider, privateKey) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }

    @Test
    fun `verify rejects a token with an unrecognized role claim`(@TempDir tempDir: File) {
        val (provider, privateKey) = newPublicKeyProvider(tempDir)
        val verifier = JwtTokenVerifierAdapter(provider)

        val token = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "SUPERUSER")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()

        assertThrows(UnauthenticatedException::class.java) { verifier.verify(token) }
    }
}
