package com.minimart.identity.web.dto

/** Matches the Phase-2 GET /api/users/me and GET /api/users/{id} 200 response shape exactly. */
data class UserProfileResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val status: String,
    val createdAt: String,
)
