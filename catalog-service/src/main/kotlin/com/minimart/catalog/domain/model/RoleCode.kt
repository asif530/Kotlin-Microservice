package com.minimart.catalog.domain.model

/**
 * The two roles fixed by BUSINESS_RULES.md §2, as carried in an access
 * token's `role` claim. catalog-service owns no `roles` table of its own
 * (that's identity-service's — see identity-service's RoleCode kdoc); this
 * is purely a claim-decoding enum for CAT-006's admin-only gate.
 */
enum class RoleCode(val dbCode: String) {
    ADMIN("ADMIN"),
    CUSTOMER("CUSTOMER"),
    ;

    companion object {
        fun fromDbCode(code: String): RoleCode =
            entries.firstOrNull { it.dbCode == code }
                ?: throw IllegalStateException("Unknown role code read from token: '$code'")
    }
}
