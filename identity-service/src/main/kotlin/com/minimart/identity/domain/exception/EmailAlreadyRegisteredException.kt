package com.minimart.identity.domain.exception

/**
 * ACC-003 / ACC-002: raised when a registration request targets an email
 * address that already has an account, including a case-only difference
 * (enforced at the database level by the `citext` unique constraint on
 * `accounts.email`, which is the actual source of truth — see
 * infrastructure.persistence.AccountRepositoryAdapter).
 */
class EmailAlreadyRegisteredException(val email: String) :
    RuntimeException("An account with this email already exists.")
