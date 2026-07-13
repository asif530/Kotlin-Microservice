package com.minimart.identity.domain.port

import com.minimart.identity.domain.model.Account

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
     * Persists a new account.
     *
     * @throws com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
     *   if the database's case-insensitive unique constraint on `email` is
     *   violated — this is the authoritative duplicate-email guard, not
     *   [existsByEmail].
     */
    fun save(account: Account): Account
}
