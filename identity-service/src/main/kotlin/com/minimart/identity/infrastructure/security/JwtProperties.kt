package com.minimart.identity.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for RSA key handling and RS256 JWT issuance.
 * Bound from `identity.security.jwt.*` in application.yml — nothing here is
 * hardcoded (see IdentityServiceApplication's @ConfigurationPropertiesScan).
 *
 * @property issuer `iss` claim value.
 * @property expirationSeconds token lifetime; also the response `expiresIn`.
 * @property keySize RSA key size in bits, used only the first time a keypair
 *   is generated (see [RsaKeyPairProvider]).
 * @property privateKeyPath filesystem path to the PKCS8 PEM private key.
 *   Local to identity-service; must never be shared.
 * @property publicKeyExportPath filesystem path the derived public key PEM
 *   is (re)written to on every startup — fixed at `gateway/keys/identity-dev-public-key.pem`
 *   by contract with the (separate) Kong gateway work item.
 */
@ConfigurationProperties(prefix = "identity.security.jwt")
data class JwtProperties(
    val issuer: String,
    val expirationSeconds: Long,
    val keySize: Int,
    val privateKeyPath: String,
    val publicKeyExportPath: String,
)
