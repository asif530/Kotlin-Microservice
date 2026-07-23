package com.minimart.order.domain.exception

/**
 * Raised for a structurally invalid checkout request that Bean Validation
 * can't cleanly express as an annotation on the request DTO — ORD-002 (at
 * least one line item) and ORD-004 (a given product at most once per
 * order). Mapped to the same VALIDATION_ERROR code Bean Validation
 * failures already use elsewhere, mirroring catalog-service's
 * ProductValidationException precedent.
 */
class OrderValidationException(message: String) : RuntimeException(message)
