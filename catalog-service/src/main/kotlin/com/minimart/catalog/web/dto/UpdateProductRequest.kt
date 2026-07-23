package com.minimart.catalog.web.dto

/**
 * CAT-010: a partial update — every field is optional, and an omitted
 * (null) field keeps its current value. Unlike CreateProductRequest, these
 * fields intentionally carry no `@NotBlank`/Bean Validation constraints:
 * `@NotBlank` rejects null, which would wrongly forbid *omitting* a field
 * from a PATCH. "Non-blank when present" is instead checked in
 * ProductService (see ProductValidationException kdoc). `stockCount` is
 * deliberately absent — CAT-010 lists only price/name/description/category
 * as mutable here; stock is CAT-011's separate authority (ReserveStock),
 * not something this admin-edit endpoint touches.
 */
data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val unitPrice: String? = null,
)
