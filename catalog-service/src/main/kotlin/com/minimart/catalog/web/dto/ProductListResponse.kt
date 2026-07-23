package com.minimart.catalog.web.dto

/** Matches the Phase-3 GET /api/products 200 response shape exactly. */
data class ProductListResponse(
    val items: List<ProductSummaryResponse>,
    val total: Int,
)

/**
 * A single item in the GET /api/products listing. `inStock` is a derived
 * boolean (`stockCount > 0`), not the raw count — CAT-007 only requires
 * marking a zero-stock item as out of stock, not publishing the exact
 * remaining quantity to a browsing customer.
 */
data class ProductSummaryResponse(
    val id: String,
    val name: String,
    val category: String,
    val unitPrice: String,
    val inStock: Boolean,
)
