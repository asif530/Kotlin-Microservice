package com.minimart.identity.web.dto

/** Matches the Phase-1 201 response shape exactly. */
data class RegisterResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val status: String,
    val createdAt: String,
)
