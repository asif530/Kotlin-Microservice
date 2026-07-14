package com.minimart.identity.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * ACC-009: only promotion to ADMIN is defined — BUSINESS_RULES.md never
 * describes a demotion/CUSTOMER path, so none is implemented here. Any
 * value other than "ADMIN" (including the otherwise-valid role code
 * "CUSTOMER") is rejected as a 400 VALIDATION_ERROR.
 */
data class UpdateRoleRequest(
    @field:NotBlank(message = "role must not be blank")
    @field:Pattern(
        regexp = "^ADMIN$",
        message = "role must be 'ADMIN' — promotion is the only supported change; no demotion path is defined",
    )
    val role: String,
)
