package com.minimart.catalog.infrastructure.security

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Loads identity-service's RSA public key so [JwtTokenVerifierAdapter] can
 * verify the RS256 tokens identity-service issues — the same public key
 * Kong's jwt plugin verifies against (kong.decl.yaml's `identity-service`
 * consumer). Unlike identity-service's RsaKeyPairProvider, this component
 * never generates or writes key material: catalog-service holds no private
 * key and is never the token issuer, only a verifier, so read-only access
 * to the already-exported public key file is all it needs.
 *
 * If the file doesn't exist yet at startup (identity-service hasn't run
 * once to export it), this fails fast with a clear error rather than
 * starting in a state where every admin-only request would otherwise throw
 * a confusing NPE/FileNotFoundException deep inside request handling.
 */
@Component
final class PublicKeyProvider(
    private val jwtProperties: JwtProperties,
) {

    private val logger = LoggerFactory.getLogger(PublicKeyProvider::class.java)

    lateinit var publicKey: PublicKey
        private set

    @PostConstruct
    fun loadPublicKey() {
        val publicKeyFile = File(jwtProperties.publicKeyPath)
        check(publicKeyFile.exists()) {
            "No RSA public key found at ${publicKeyFile.absolutePath}. This file is exported by " +
                "identity-service's RsaKeyPairProvider on its own startup — start identity-service " +
                "at least once before catalog-service, or point " +
                "catalog.security.jwt.public-key-path at an existing key."
        }

        val base64Body = publicKeyFile.readText()
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        val keyBytes = Base64.getDecoder().decode(base64Body)
        val keySpec = X509EncodedKeySpec(keyBytes)
        publicKey = KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(keySpec)
        logger.info("Loaded identity-service RSA public key from {}", publicKeyFile.absolutePath)
    }

    private companion object {
        const val RSA_ALGORITHM = "RSA"
    }
}
