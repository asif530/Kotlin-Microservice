package com.minimart.catalog.web.dto

/**
 * Matches the Phase-4 PATCH /api/products/{id} 200 response shape exactly —
 * deliberately narrower than CreateProductResponse (no description/
 * category echoed back), per the doc's own example.
 */
data class UpdateProductResponse(
    val id: String,
    val name: String,
    val unitPrice: String,
    val stockCount: Int,
    val status: String,
    val updatedAt: String,
)
