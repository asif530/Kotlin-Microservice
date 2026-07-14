package com.minimart.identity.domain.port

import com.minimart.identity.domain.model.Account
import java.util.UUID

/**
 * Outbound port (Clean Architecture / dependency inversion): the application
 * layer depends on this interface only, never on Spring Data JPA directly.
 * Implemented by infrastructure.persistence.AccountRepositoryAdapter.
 */
interface AccountRepositoryPort {

    /**
     * Case-insensitive existence check (ACC-002), used as a fast,
     * friendlier pre-check ahead of [save]. This is a best-effort UX
     * optimization only — the database's `citext` unique constraint,
     * enforced inside [save], remains the actual source of truth, since a
     * pre-check-then-insert has an inherent time-of-check/time-of-use race.
     */
    fun existsByEmail(email: String): Boolean

    /** Case-insensitive lookup (ACC-002/ACC-005), or null if no such account exists. */
    fun findByEmail(email: String): Account?

    /**
     * Lookup by primary key (Phase-2: resolving the caller behind a token,
     * ACC-011's admin lookup, and the target of ACC-008/ACC-009 admin
     * actions), or null if no such account exists.
     */
    fun findById(id: UUID): Account?

    /**
     * Persists a new account.
     *
     * @throws com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
     *   if the database's case-insensitive unique constraint on `email` is
     *   violated — this is the authoritative duplicate-email guard, not
     *   [existsByEmail].
     */
    fun save(account: Account): Account

    /**
     * Persists changes to an existing account's mutable fields (`fullName`,
     * `role`, `status`, `updatedAt`) — Phase-2's self-service profile update
     * (ACC-011) and admin status/role changes (ACC-008/ACC-009). Unlike
     * [save], this never inserts a new row and never touches `email`, which
     * is immutable through every Phase-2 endpoint.
     */
    fun update(account: Account): Account
}
