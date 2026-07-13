package com.minimart.identity.infrastructure.persistence

import com.minimart.identity.domain.model.AccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA mapping for the `accounts` table (see V1__create_identity_schema.sql).
 *
 * CITEXT note: `email` has no native Hibernate/JDBC type. It is mapped here
 * as a plain `String` with no custom Hibernate `UserType` and no
 * `columnDefinition` override. This is deliberate, not an oversight: the
 * Postgres `citext` extension defines an implicit cast from `text`/`varchar`
 * to `citext`, so a driver-level `VARCHAR` bind value is accepted for both
 * inserts and the `=` comparison Spring Data generates for
 * `findByEmail`/`existsByEmail` — case-insensitive matching happens for free
 * at the database, which is exactly what ACC-002 requires. Re-implementing
 * case-folding in Kotlin (e.g. `.lowercase()` before every query) was
 * explicitly avoided per the task brief: it would duplicate a guarantee the
 * database already owns and risks silently diverging from it (e.g. a
 * locale-sensitive `.lowercase()` disagreeing with Postgres's case-folding
 * for non-ASCII characters).
 */
@Entity
@Table(name = "accounts")
class AccountJpaEntity(
    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    var role: RoleJpaEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 11)
    var status: AccountStatus,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
