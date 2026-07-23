package com.minimart.catalog.domain.exception

/**
 * Raised by PATCH /api/products/{id} when a field the caller explicitly
 * included is blank (name/description/category must stay non-blank per
 * CAT-001, same as at creation time). Bean Validation can't express this
 * declaratively on [com.minimart.catalog.web.dto.UpdateProductRequest] — its
 * fields are nullable-and-optional (a PATCH only touches the fields it
 * includes), and `@NotBlank` rejects null, which would wrongly forbid
 * *omitting* a field. So "non-blank when present" is checked in
 * ProductService instead, deliberately raising the same VALIDATION_ERROR
 * code Bean Validation failures already use elsewhere in this service, so
 * the caller sees one consistent code for "malformed request" regardless
 * of which layer caught it.
 */
class ProductValidationException(message: String) : RuntimeException(message)
