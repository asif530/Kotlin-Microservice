package com.minimart.identity.application.dto

import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import java.util.UUID

/** Application-layer input for ACC-008's admin-only deactivate/reactivate (PATCH /api/users/{id}/status). */
data class UpdateAccountStatusCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetAccountId: UUID,
    val newStatus: AccountStatus,
)
