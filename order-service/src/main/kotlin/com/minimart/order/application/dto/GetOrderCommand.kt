package com.minimart.order.application.dto

import com.minimart.order.domain.model.RoleCode
import java.util.UUID

/** GET /api/orders/{id} (scenario 19). */
data class GetOrderCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val orderId: UUID,
)
