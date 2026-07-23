package com.minimart.catalog.application.dto

/**
 * GET /api/products (CAT-006: public browsing/search, no caller identity
 * involved). [category] is an optional exact-match filter — the only search
 * parameter the Phase-3 doc's own example demonstrates (`?category=Footwear`).
 */
data class ListProductsCommand(val category: String?)
