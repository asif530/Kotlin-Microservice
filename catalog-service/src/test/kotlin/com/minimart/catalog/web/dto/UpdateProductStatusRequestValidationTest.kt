package com.minimart.catalog.web.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** CAT-008: status must be exactly ACTIVE or DEACTIVATED — mirrors identity-service's UpdateStatusRequest tests. */
class UpdateProductStatusRequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `ACTIVE has no violations`() {
        assertTrue(validator.validate(UpdateProductStatusRequest("ACTIVE")).isEmpty())
    }

    @Test
    fun `DEACTIVATED has no violations`() {
        assertTrue(validator.validate(UpdateProductStatusRequest("DEACTIVATED")).isEmpty())
    }

    @Test
    fun `blank status is rejected`() {
        assertTrue(validator.validate(UpdateProductStatusRequest("")).isNotEmpty())
    }

    @Test
    fun `an unrecognized status value is rejected`() {
        assertTrue(validator.validate(UpdateProductStatusRequest("ARCHIVED")).isNotEmpty())
    }

    @Test
    fun `lowercase is rejected — the wire contract is uppercase only`() {
        assertTrue(validator.validate(UpdateProductStatusRequest("active")).isNotEmpty())
    }
}
