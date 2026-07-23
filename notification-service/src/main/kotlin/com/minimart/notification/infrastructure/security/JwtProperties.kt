package com.minimart.notification.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for RS256 token verification. Bound from
 * `notification.security.jwt.*` in application.yml — mirrors
 * catalog-service/order-service's own JwtProperties exactly.
 *
 * @property publicKeyPath filesystem path to identity-service's exported
 *   public key PEM. notification-service only ever reads this file.
 */
@ConfigurationProperties(prefix = "notification.security.jwt")
data class JwtProperties(
    val publicKeyPath: String,
)
