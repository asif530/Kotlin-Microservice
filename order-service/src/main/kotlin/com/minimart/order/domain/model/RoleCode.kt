package com.minimart.order.domain.model

/**
 * The two roles fixed by BUSINESS_RULES.md §2, as carried in an access
 * token's `role` claim. order-service owns no `roles` table of its own
 * (that's identity-service's) — this is purely a claim-decoding enum for
 * ORD-013's admin/self visibility split, mirroring catalog-service's own
 * RoleCode.
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
