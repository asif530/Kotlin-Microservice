package com.minimart.catalog.domain.exception

import java.util.UUID

/**
 * Raised by DELETE /api/products/{id} when CAT-009 blocks a permanent
 * delete — the product has been part of at least one placed order and can
 * only be Deactivated. Message matches the Phase-4 doc's 409 response body
 * verbatim.
 */
class ProductHasOrderHistoryException(val productId: UUID) :
    RuntimeException("This product has been part of at least one order and can only be deactivated, not deleted.")
