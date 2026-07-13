package com.minimart.identity.web.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ACC-001: presence of email/password/fullName is required. Deliberately no
 * minimum length/complexity check on password — BUSINESS_RULES.md specifies
 * none, so none is invented (see RegisterRequest kdoc).
 */
class RequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `valid register request has no violations`() {
        val request = RegisterRequest("alice@example.test", "correct-horse-battery-staple", "Alice Nguyen")
        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `blank email on register is rejected`() {
        val request = RegisterRequest("", "password", "Alice")
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `malformed email on register is rejected`() {
        val request = RegisterRequest("not-an-email", "password", "Alice")
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `blank password on register is rejected`() {
        val request = RegisterRequest("alice@example.test", "", "Alice")
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `blank fullName on register is rejected`() {
        val request = RegisterRequest("alice@example.test", "password", "")
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `a single-character password is accepted — no invented complexity rule`() {
        val request = RegisterRequest("alice@example.test", "x", "Alice")
        assertEquals(0, validator.validate(request).size)
    }

    @Test
    fun `valid login request has no violations`() {
        val request = LoginRequest("alice@example.test", "any-password")
        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `blank email or password on login is rejected`() {
        assertTrue(validator.validate(LoginRequest("", "pw")).isNotEmpty())
        assertTrue(validator.validate(LoginRequest("alice@example.test", "")).isNotEmpty())
    }
}
