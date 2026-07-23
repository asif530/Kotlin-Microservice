package com.minimart.notification.domain.model

/**
 * The two roles fixed by BUSINESS_RULES.md §2, as carried in an access
 * token's `role` claim. notification-service owns no `roles` table of its
 * own — this is purely a claim-decoding enum for NTF-003's admin/self
 * visibility split, mirroring catalog-service/order-service's own RoleCode.
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
