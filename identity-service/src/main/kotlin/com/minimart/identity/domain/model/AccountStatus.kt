package com.minimart.identity.domain.model

/** ACC-006: an account has exactly one status at all times. */
enum class AccountStatus {
    ACTIVE,
    DEACTIVATED,
}
