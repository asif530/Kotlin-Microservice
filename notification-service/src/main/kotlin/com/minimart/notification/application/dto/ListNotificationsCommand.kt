package com.minimart.notification.application.dto

import com.minimart.notification.domain.model.RoleCode
import java.util.UUID

/**
 * GET /api/notifications (scenarios 26/27). [accountIdFilter] is the raw
 * `?accountId=` query parameter, before NTF-003's authorization rule is
 * applied — mirrors order-service's ListOrdersCommand exactly.
 */
data class ListNotificationsCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val accountIdFilter: UUID?,
)
