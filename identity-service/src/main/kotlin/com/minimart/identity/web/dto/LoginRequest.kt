package com.minimart.identity.web.dto

import jakarta.validation.constraints.NotBlank

/**
 * Only presence is validated here (not email format). Assumption: the
 * Phase-1 doc and BUSINESS_RULES.md never describe a 400 path for login —
 * only the 200/401 outcomes (ACC-005) — so a malformed-but-nonblank email at
 * login is left to fall through to the normal "no such account" branch
 * (INVALID_CREDENTIALS, 401) rather than a format-validation 400. This also
 * avoids adding a distinguishable response path that isn't one of the two
 * ACC-005/§ doc-specified outcomes.
 */
data class LoginRequest(
    @field:NotBlank(message = "email must not be blank")
    val email: String,

    @field:NotBlank(message = "password must not be blank")
    val password: String,
)
