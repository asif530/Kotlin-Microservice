package com.minimart.identity.domain.exception

import java.util.UUID

/**
 * Raised when an Administrator looks up or acts on an account id that does
 * not exist (GET /api/users/{id}, PATCH .../status, PATCH .../role). The
 * Phase-2 doc's response examples don't show this case explicitly — 404
 * with code ACCOUNT_NOT_FOUND is this implementation's own choice, chosen
 * to mirror the project's existing resource-oriented error-code convention
 * (see EMAIL_ALREADY_REGISTERED).
 */
class AccountNotFoundException(val accountId: UUID) : RuntimeException("No account exists with the given id.")
