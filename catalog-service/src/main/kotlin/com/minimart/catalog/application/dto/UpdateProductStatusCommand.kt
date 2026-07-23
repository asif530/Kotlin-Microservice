package com.minimart.catalog.application.dto

import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.domain.model.RoleCode
import java.util.UUID

/** PATCH /api/products/{id}/status (CAT-008, CAT-006, scenario 14). */
data class UpdateProductStatusCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetProductId: UUID,
    val newStatus: ProductStatus,
)
