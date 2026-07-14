package com.minimart.identity.web.dto

/** Matches the Phase-2 PATCH /api/users/{id}/role 200 response shape exactly. */
data class UpdateRoleResponse(
    val id: String,
    val role: String,
    val updatedAt: String,
)
