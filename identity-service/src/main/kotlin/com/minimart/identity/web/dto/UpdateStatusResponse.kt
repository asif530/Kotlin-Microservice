package com.minimart.identity.web.dto

/** Matches the Phase-2 PATCH /api/users/{id}/status 200 response shape exactly. */
data class UpdateStatusResponse(
    val id: String,
    val status: String,
    val updatedAt: String,
)
