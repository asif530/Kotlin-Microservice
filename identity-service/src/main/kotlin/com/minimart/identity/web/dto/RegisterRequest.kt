package com.minimart.identity.web.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * ACC-001: email, password, full name required. BUSINESS_RULES.md specifies
 * no minimum password length/complexity rule, so none is invented here —
 * only presence (non-blank) is validated, plus a standard email-format
 * check on `email`.
 */
data class RegisterRequest(
    @field:NotBlank(message = "email must not be blank")
    @field:Email(message = "email must be a well-formed email address")
    val email: String,

    @field:NotBlank(message = "password must not be blank")
    val password: String,

    @field:NotBlank(message = "fullName must not be blank")
    val fullName: String,
)
