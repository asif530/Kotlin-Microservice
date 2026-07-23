package com.minimart.order.domain.exception

/**
 * Raised by POST /api/orders when the caller is not an eligible buyer —
 * ORD-001: no guest checkout, and a Deactivated Customer cannot place new
 * orders. Message matches the Phase-5 doc's response body verbatim; per
 * that same doc, this is deliberately a single, undifferentiated cause —
 * the same ACC-005 "identical error" posture already established for
 * login — so a missing account and a Deactivated account produce the
 * exact same body (see domain.port.IdentityClientPort kdoc for where that
 * collapsing actually happens).
 */
class NotEligibleToOrderException : RuntimeException("This account cannot place an order.")
