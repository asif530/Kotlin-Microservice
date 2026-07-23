package com.minimart.catalog.application.dto

import com.minimart.catalog.domain.model.RoleCode
import java.util.UUID

/**
 * POST /api/products (CAT-001..CAT-004, CAT-006). [unitPriceRaw] stays a
 * String at this boundary — CAT-002's ">strictly zero" check and the
 * "is this even a number" check both belong to the use-case interactor
 * (ProductService), not this transport-adjacent DTO, so the doc's specific
 * INVALID_PRICE code (rather than a generic VALIDATION_ERROR) can be raised
 * for a zero/negative/malformed price.
 */
data class CreateProductCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val name: String,
    val description: String,
    val category: String,
    val unitPriceRaw: String,
    val stockCount: Int,
)
