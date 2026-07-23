package com.minimart.catalog.domain.exception

/**
 * Raised when an authenticated caller is not permitted to perform the
 * requested action (CAT-006). [message] is supplied by the call site
 * verbatim, matching the Phase-3 doc's fixed 403 wording exactly ("Only an
 * Administrator can create a product.").
 */
class ForbiddenActionException(message: String) : RuntimeException(message)
