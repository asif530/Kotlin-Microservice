package com.minimart.identity.web.dto

/** Matches the Phase-2 PATCH /api/users/me 200 response shape exactly (updatedAt, not createdAt). */
data class UpdateProfileResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val status: String,
    val updatedAt: String,
)
