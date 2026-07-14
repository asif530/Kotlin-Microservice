package com.minimart.identity.application.dto

import com.minimart.identity.domain.model.RoleCode
import java.util.UUID

/**
 * Application-layer input for ACC-009's admin-only promotion to
 * Administrator (PATCH /api/users/{id}/role). There is no corresponding
 * "demote" command — BUSINESS_RULES.md never defines a demotion path, so
 * none is implemented (see UserAccountUseCase kdoc).
 */
data class PromoteToAdminCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetAccountId: UUID,
)
