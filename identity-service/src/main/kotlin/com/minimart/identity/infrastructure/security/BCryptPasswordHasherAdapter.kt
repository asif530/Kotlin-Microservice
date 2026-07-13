package com.minimart.identity.infrastructure.security

import com.minimart.identity.domain.port.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/** Adapter over Spring Security's BCryptPasswordEncoder bean (see PasswordEncoderConfig). */
@Component
class BCryptPasswordHasherAdapter(
    private val passwordEncoder: PasswordEncoder,
) : PasswordHasher {

    override fun hash(rawPassword: String): String =
        requireNotNull(passwordEncoder.encode(rawPassword)) { "PasswordEncoder.encode returned null" }

    override fun matches(rawPassword: String, hashedPassword: String): Boolean =
        passwordEncoder.matches(rawPassword, hashedPassword)
}
