package com.minimart.identity.domain.exception

/**
 * Raised when an authenticated caller is not permitted to perform the
 * requested action (ACC-008, ACC-009, ACC-011). [message] is supplied by
 * each call site verbatim, since the Phase-2 doc fixes a distinct 403
 * message per admin-only endpoint ("You can only view your own account.",
 * "Only an Administrator can change an account's status.", "Only an
 * Administrator can grant the Administrator role.") rather than one generic
 * wording.
 */
class ForbiddenActionException(message: String) : RuntimeException(message)
