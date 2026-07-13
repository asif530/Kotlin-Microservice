package com.minimart.identity.infrastructure.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class BCryptPasswordHasherAdapterTest {

    private val adapter = BCryptPasswordHasherAdapter(BCryptPasswordEncoder(4)) // low strength: fast tests

    @Test
    fun `hash never returns the raw password`() {
        val hash = adapter.hash("correct-horse-battery-staple")
        assertNotEquals("correct-horse-battery-staple", hash)
    }

    @Test
    fun `matches is true for the password that produced the hash`() {
        val hash = adapter.hash("s3cret-password")
        assertTrue(adapter.matches("s3cret-password", hash))
    }

    @Test
    fun `matches is false for a different password`() {
        val hash = adapter.hash("s3cret-password")
        assertFalse(adapter.matches("wrong-password", hash))
    }

    @Test
    fun `hashing the same password twice yields different hashes due to per-call salting`() {
        val first = adapter.hash("same-password")
        val second = adapter.hash("same-password")
        assertNotEquals(first, second)
        // ...but both still verify correctly.
        assertTrue(adapter.matches("same-password", first))
        assertTrue(adapter.matches("same-password", second))
    }
}
