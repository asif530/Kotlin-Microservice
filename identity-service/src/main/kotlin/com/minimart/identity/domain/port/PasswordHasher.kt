package com.minimart.identity.domain.port

/**
 * Outbound port for one-way password hashing/verification (ACC-001).
 * Implemented by infrastructure.security.BCryptPasswordHasherAdapter.
 */
interface PasswordHasher {

    /** Produces a salted hash of [rawPassword]. Never returns the raw input. */
    fun hash(rawPassword: String): String

    /** True if [rawPassword] hashes to [hashedPassword]. */
    fun matches(rawPassword: String, hashedPassword: String): Boolean
}
