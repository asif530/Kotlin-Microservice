package com.minimart.catalog.application.dto

import com.minimart.catalog.domain.model.RoleCode
import java.util.UUID

/**
 * PATCH /api/products/{id} (CAT-010, CAT-006, scenario 13). A partial
 * update — only the fields the caller actually sent are non-null here, and
 * only those are changed; an omitted field keeps its current value.
 * [unitPriceRaw] stays a String for the same reason
 * CreateProductCommand.unitPriceRaw does — CAT-002's positivity check
 * happens in ProductService, not at the DTO boundary.
 */
data class UpdateProductCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetProductId: UUID,
    val name: String?,
    val description: String?,
    val category: String?,
    val unitPriceRaw: String?,
)
