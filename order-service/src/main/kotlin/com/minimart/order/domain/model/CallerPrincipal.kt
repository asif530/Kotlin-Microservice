package com.minimart.order.domain.model

import java.util.UUID

/**
 * The authenticated caller resolved from a verified access token — account
 * id (`sub` claim) and role (`role` claim), the two facts ORD-001/ORD-013
 * need. Mirrors catalog-service's own CallerPrincipal.
 */
data class CallerPrincipal(
    val accountId: UUID,
    val role: RoleCode,
)
