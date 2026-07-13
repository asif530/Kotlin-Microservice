package com.minimart.identity.application.testsupport

import com.minimart.identity.domain.port.PasswordHasher

/**
 * Deliberately non-cryptographic — isolates AuthService's orchestration
 * logic from BCrypt's actual algorithm, which has its own dedicated test
 * (BCryptPasswordHasherAdapterTest).
 */
class FakePasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): String = "hashed:$rawPassword"

    override fun matches(rawPassword: String, hashedPassword: String): Boolean =
        hashedPassword == "hashed:$rawPassword"
}
