package com.minimart.catalog.web.dto

/** Matches the Phase-4 PATCH /api/products/{id}/status 200 response shape exactly. */
data class UpdateProductStatusResponse(
    val id: String,
    val status: String,
    val updatedAt: String,
)
