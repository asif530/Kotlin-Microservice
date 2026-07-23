package com.minimart.catalog.web.dto

/** Matches the Phase-3 POST /api/products 201 response shape exactly. */
data class CreateProductResponse(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unitPrice: String,
    val stockCount: Int,
    val status: String,
    val createdAt: String,
)
