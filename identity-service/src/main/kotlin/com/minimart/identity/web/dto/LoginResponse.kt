package com.minimart.identity.web.dto

/** Matches the Phase-1 200 response shape exactly. */
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
