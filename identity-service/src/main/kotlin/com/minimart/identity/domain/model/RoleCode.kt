package com.minimart.identity.domain.model

/**
 * The two roles fixed by BUSINESS_RULES.md §2. [dbId] and [dbCode] mirror the
 * already-locked, already-seeded `roles` lookup table (see
 * V2__seed_roles.sql) exactly — they are not invented here, only referenced.
 */
enum class RoleCode(val dbId: Short, val dbCode: String) {
    ADMIN(1, "ADMIN"),
    CUSTOMER(2, "CUSTOMER"),
    ;

    companion object {
        fun fromDbCode(code: String): RoleCode =
            entries.firstOrNull { it.dbCode == code }
                ?: throw IllegalStateException("Unknown role code read from database: '$code'")
    }
}
