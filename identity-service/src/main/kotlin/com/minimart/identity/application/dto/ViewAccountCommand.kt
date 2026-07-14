package com.minimart.identity.application.dto

import com.minimart.identity.domain.model.RoleCode
import java.util.UUID

/**
 * Application-layer input for ACC-011's admin account lookup
 * (GET /api/users/{id}). [callerRole] is the role claim resolved from the
 * caller's verified access token (see domain.port.TokenVerifier), not a
 * fresh database read of the caller's current role.
 */
data class ViewAccountCommand(
    val callerId: UUID,
    val callerRole: RoleCode,
    val targetAccountId: UUID,
)
