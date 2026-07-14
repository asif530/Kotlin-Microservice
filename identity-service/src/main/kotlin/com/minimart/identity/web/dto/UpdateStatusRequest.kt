package com.minimart.identity.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * ACC-008/ACC-006: status is always exactly one of ACTIVE/DEACTIVATED, so
 * this single field/endpoint handles both deactivate and reactivate. Any
 * other value is a 400 VALIDATION_ERROR — not shown explicitly by the
 * Phase-2 doc, but consistent with this project's existing Bean Validation
 * pattern (see RegisterRequest).
 */
data class UpdateStatusRequest(
    @field:NotBlank(message = "status must not be blank")
    @field:Pattern(regexp = "^(ACTIVE|DEACTIVATED)$", message = "status must be either 'ACTIVE' or 'DEACTIVATED'")
    val status: String,
)
