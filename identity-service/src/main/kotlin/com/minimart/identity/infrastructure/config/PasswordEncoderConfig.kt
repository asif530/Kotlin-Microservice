package com.minimart.identity.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * BCryptPasswordEncoder only — not the full spring-boot-starter-security
 * filter chain. Per ARCHITECTURE.md §7, Kong owns authentication/JWT
 * verification at the gateway; identity-service just needs a hashing
 * primitive for ACC-001.
 */
@Configuration
class PasswordEncoderConfig(
    @Value("\${identity.security.password.bcrypt-strength}") private val bcryptStrength: Int,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(bcryptStrength)
}
