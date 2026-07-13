package com.minimart.identity.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Maps the `roles` lookup table seeded by V2__seed_roles.sql. `id`/`code`
 * are never written by application code — this table is reference data
 * only (see com.minimart.identity.domain.model.RoleCode for the fixed
 * ADMIN/CUSTOMER values this mirrors).
 */
@Entity
@Table(name = "roles")
class RoleJpaEntity(
    @Id
    @Column(name = "id")
    val id: Short,

    @Column(name = "code", nullable = false, unique = true, length = 20)
    val code: String,
)
