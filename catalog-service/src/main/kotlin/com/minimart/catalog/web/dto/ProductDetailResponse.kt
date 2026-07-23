package com.minimart.catalog.web.dto

/** Matches the Phase-3 GET /api/products/{id} 200 response shape exactly. */
data class ProductDetailResponse(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unitPrice: String,
    val inStock: Boolean,
)
