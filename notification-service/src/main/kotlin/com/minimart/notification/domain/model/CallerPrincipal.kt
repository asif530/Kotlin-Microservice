package com.minimart.notification.domain.model

import java.util.UUID

/**
 * The authenticated caller resolved from a verified access token — account
 * id (`sub` claim) and role (`role` claim), the two facts NTF-003 needs.
 * Mirrors catalog-service/order-service's own CallerPrincipal.
 */
data class CallerPrincipal(
    val accountId: UUID,
    val role: RoleCode,
)
