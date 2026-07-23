package com.minimart.order.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for RS256 token verification. Bound from
 * `order.security.jwt.*` in application.yml — mirrors catalog-service's
 * own JwtProperties exactly.
 *
 * @property publicKeyPath filesystem path to identity-service's exported
 *   public key PEM. order-service only ever reads this file.
 */
@ConfigurationProperties(prefix = "order.security.jwt")
data class JwtProperties(
    val publicKeyPath: String,
)
