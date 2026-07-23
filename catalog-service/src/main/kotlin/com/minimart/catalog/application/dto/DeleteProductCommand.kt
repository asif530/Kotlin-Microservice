package com.minimart.catalog.application.dto

import com.minimart.catalog.domain.model.RoleCode
import java.util.UUID

/** DELETE /api/products/{id} (CAT-009, CAT-006, scenario 15). */
data class DeleteProductCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetProductId: UUID,
)
