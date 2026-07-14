package com.minimart.identity.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * ACC-011: a Customer can update their own profile — name only. Email is
 * the account's unique identifier and isn't part of this rule (Phase-2
 * doc); changing it is new scope, not built here, so no `email` field
 * exists on this request at all.
 */
data class UpdateProfileRequest(
    @field:NotBlank(message = "fullName must not be blank")
    val fullName: String,
)
