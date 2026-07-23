package com.minimart.catalog.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

/**
 * CAT-001: name, description, category, unit price, stock count required.
 * CAT-004: a product belongs to exactly one category — trivially satisfied
 * by [category] being a single field, not a collection, so no extra runtime
 * check is needed for "exactly one."
 *
 * [unitPrice] stays a String (matching the Phase-3 doc's own request/response
 * examples, `"unitPrice": "89.99"`) so the exact decimal the client sent is
 * preserved rather than round-tripped through a binary floating-point type.
 * Only its *presence* is validated here (CAT-001) — CAT-002's ">strictly
 * zero" rule is checked by ProductService, which is what lets a zero/negative
 * price raise the Phase-3 doc's specific INVALID_PRICE code instead of a
 * generic VALIDATION_ERROR.
 *
 * [stockCount] is a plain (non-nullable) Kotlin Int: a missing field or a
 * non-integer JSON value (e.g. `25.5`) already fails Jackson deserialization
 * before validation even runs, satisfying CAT-003's "whole number" half;
 * [PositiveOrZero] covers the "non-negative" half.
 */
data class CreateProductRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,

    @field:NotBlank(message = "description must not be blank")
    val description: String,

    @field:NotBlank(message = "category must not be blank")
    val category: String,

    @field:NotBlank(message = "unitPrice must not be blank")
    val unitPrice: String,

    @field:PositiveOrZero(message = "stockCount must be zero or greater")
    val stockCount: Int,
)
