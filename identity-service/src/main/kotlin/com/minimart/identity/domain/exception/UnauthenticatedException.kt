package com.minimart.identity.domain.exception

/**
 * Raised when a protected route (Phase-2's /api/users endpoints) is
 * called with a missing, malformed, expired, or otherwise invalid access
 * token. Not a Phase-1/BUSINESS_RULES.md-named case — the source documents
 * fix the *authorization* outcomes (ACC-008/009/011's 403 bodies) but never
 * describe the *authentication* failure itself, since Phase 1 had no
 * protected route yet. UNAUTHORIZED/401 is this implementation's own
 * judgment call, following the same "single, undifferentiated cause"
 * philosophy ACC-005/InvalidCredentialsException already established for
 * login: no detail is given here either, since distinguishing "missing
 * header" from "expired token" from "bad signature" to a caller has no
 * legitimate use and is a minor information leak about the verification
 * mechanism.
 */
class UnauthenticatedException(message: String = "Authentication is required.") : RuntimeException(message)
