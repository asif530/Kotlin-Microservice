package com.minimart.identity.web.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Bean Validation coverage for Phase-2's request DTOs, mirroring
 * RequestValidationTest's style for Phase-1.
 */
class UserRequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ---- UpdateProfileRequest (PATCH /api/users/me) -------------------------------------------

    @Test
    fun `valid update-profile request has no violations`() {
        assertTrue(validator.validate(UpdateProfileRequest("Alice N. Nguyen")).isEmpty())
    }

    @Test
    fun `blank fullName on update-profile is rejected`() {
        assertTrue(validator.validate(UpdateProfileRequest("")).isNotEmpty())
    }

    // ---- UpdateStatusRequest (PATCH /api/users/{id}/status) ------------------------------------

    @Test
    fun `ACTIVE and DEACTIVATED are both accepted on update-status`() {
        assertTrue(validator.validate(UpdateStatusRequest("ACTIVE")).isEmpty())
        assertTrue(validator.validate(UpdateStatusRequest("DEACTIVATED")).isEmpty())
    }

    @Test
    fun `blank status on update-status is rejected`() {
        assertTrue(validator.validate(UpdateStatusRequest("")).isNotEmpty())
    }

    @Test
    fun `an unrecognized status value is rejected`() {
        assertTrue(validator.validate(UpdateStatusRequest("SUSPENDED")).isNotEmpty())
    }

    @Test
    fun `status is case-sensitive — lowercase is rejected, no case-folding invented`() {
        assertTrue(validator.validate(UpdateStatusRequest("active")).isNotEmpty())
    }

    // ---- UpdateRoleRequest (PATCH /api/users/{id}/role) -----------------------------------------

    @Test
    fun `ADMIN is accepted on update-role`() {
        assertTrue(validator.validate(UpdateRoleRequest("ADMIN")).isEmpty())
    }

    @Test
    fun `CUSTOMER is rejected on update-role — no demotion path is defined by ACC-009`() {
        assertTrue(validator.validate(UpdateRoleRequest("CUSTOMER")).isNotEmpty())
    }

    @Test
    fun `blank role on update-role is rejected`() {
        assertTrue(validator.validate(UpdateRoleRequest("")).isNotEmpty())
    }

    @Test
    fun `an unrecognized role value is rejected`() {
        assertTrue(validator.validate(UpdateRoleRequest("SUPERUSER")).isNotEmpty())
    }
}
