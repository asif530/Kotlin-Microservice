package com.minimart.catalog.domain.exception

import java.util.UUID

/**
 * Raised for GET /api/products/{id} when no visible product exists with the
 * given id — either because it truly doesn't exist, or because it's
 * Deactivated (CAT-008: "treated as if it doesn't exist to a browsing
 * customer — not a 403, since its existence itself isn't meant to be
 * visible"). Both cases produce the identical 404 PRODUCT_NOT_FOUND body
 * the Phase-3 doc fixes, deliberately indistinguishable to the caller.
 */
class ProductNotFoundException(val productId: UUID) : RuntimeException("No product with this id.")
