package com.minimart.order.application.dto

import com.minimart.order.domain.model.RoleCode
import java.util.UUID

/**
 * GET /api/orders (scenarios 19/20). [customerIdFilter] is the raw
 * `?customerId=` query parameter, before ORD-013's authorization rule is
 * applied — that resolution (whose orders the caller actually ends up
 * seeing) happens in OrderService, not here.
 */
data class ListOrdersCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val customerIdFilter: UUID?,
)
