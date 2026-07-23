package com.minimart.catalog.domain.exception

/**
 * Raised when a product's unit price fails CAT-002 (strictly greater than
 * zero — "This system has no concept of a free or promotional item"). Also
 * used for a `unitPrice` string that isn't a valid decimal number at all;
 * the Phase-3 doc doesn't show that case explicitly, but it's the same
 * underlying problem — an unusable price — so it gets the same INVALID_PRICE
 * code with a message tailored to which failure actually occurred.
 */
class InvalidPriceException(message: String) : RuntimeException(message)
