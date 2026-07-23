package com.minimart.catalog.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * CAT-008: status is always exactly one of ACTIVE/DEACTIVATED, so this
 * single field/endpoint handles both deactivate and reactivate — mirrors
 * identity-service's UpdateStatusRequest (ACC-008) exactly. Any other
 * value is a 400 VALIDATION_ERROR, not shown explicitly by the Phase-4 doc
 * but consistent with that existing pattern.
 */
data class UpdateProductStatusRequest(
    @field:NotBlank(message = "status must not be blank")
    @field:Pattern(regexp = "^(ACTIVE|DEACTIVATED)$", message = "status must be either 'ACTIVE' or 'DEACTIVATED'")
    val status: String,
)
