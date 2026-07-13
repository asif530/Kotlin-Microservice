package com.minimart.identity.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.Signature

class RsaKeyPairProviderTest {

    @Test
    fun `generates a keypair, persists the private key PEM, and exports a matching public key PEM`(@TempDir tempDir: File) {
        val privateKeyFile = File(tempDir, "nested/private.pem")
        val publicKeyFile = File(tempDir, "nested/public.pem")
        val properties = JwtProperties(
            issuer = "identity-service",
            expirationSeconds = 3600,
            keySize = 2048,
            privateKeyPath = privateKeyFile.path,
            publicKeyExportPath = publicKeyFile.path,
        )

        val provider = RsaKeyPairProvider(properties)
        provider.initializeKeyPair()

        assertTrue(privateKeyFile.exists(), "private key file should have been created")
        assertTrue(publicKeyFile.exists(), "public key file should have been created")
        assertTrue(privateKeyFile.readText().contains("-----BEGIN PRIVATE KEY-----"))
        assertTrue(publicKeyFile.readText().contains("-----BEGIN PUBLIC KEY-----"))

        // The derived public key must actually verify a signature made with the private key.
        val payload = "round-trip-check".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(provider.privateKey)
            update(payload)
        }.sign()

        val verified = Signature.getInstance("SHA256withRSA").apply {
            initVerify(provider.publicKey)
            update(payload)
        }.verify(signature)

        assertTrue(verified, "public key derived from the private key must verify its own signature")
    }

    @Test
    fun `reloads the same private key on a second startup instead of generating a new one`(@TempDir tempDir: File) {
        val privateKeyFile = File(tempDir, "private.pem")
        val publicKeyFile = File(tempDir, "public.pem")
        val properties = JwtProperties("identity-service", 3600, 2048, privateKeyFile.path, publicKeyFile.path)

        val firstProvider = RsaKeyPairProvider(properties)
        firstProvider.initializeKeyPair()
        val firstKeyPem = privateKeyFile.readText()

        val secondProvider = RsaKeyPairProvider(properties)
        secondProvider.initializeKeyPair()
        val secondKeyPem = privateKeyFile.readText()

        assertEquals(firstKeyPem, secondKeyPem, "an existing private key file must be reused, not regenerated")
        assertEquals(firstProvider.publicKey, secondProvider.publicKey)
    }
}
