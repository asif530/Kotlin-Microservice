package com.minimart.catalog.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for RS256 token verification. Bound from
 * `catalog.security.jwt.*` in application.yml (see
 * CatalogServiceApplication's @EnableConfigurationProperties).
 *
 * @property publicKeyPath filesystem path to identity-service's exported
 *   public key PEM (identity-service's JwtProperties.publicKeyExportPath) —
 *   fixed at `gateway/keys/identity-dev-public-key.pem` by the same
 *   contract Kong's jwt plugin relies on. catalog-service only ever reads
 *   this file.
 */
@ConfigurationProperties(prefix = "catalog.security.jwt")
data class JwtProperties(
    val publicKeyPath: String,
)
