package com.minimart.identity.domain.model

import java.time.Instant
import java.util.UUID

/**
 * The identity-service aggregate root. Deliberately a plain Kotlin data
 * class with no persistence annotations — JPA is an infrastructure concern
 * (see infrastructure.persistence.AccountJpaEntity) and must never leak into
 * the domain layer.
 */
data class Account(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val fullName: String,
    val role: RoleCode,
    val status: AccountStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
