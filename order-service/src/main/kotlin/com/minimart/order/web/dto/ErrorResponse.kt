package com.minimart.order.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

/** The flat error envelope used project-wide: {"error":{"code":"...","message":"..."}}. */
data class ErrorResponse(val error: ErrorBody)

/**
 * [details] is only ever populated for INSUFFICIENT_STOCK (scenario 17) —
 * `@JsonInclude(NON_NULL)` keeps every other error response's JSON
 * identical to identity-service/catalog-service's plain `{code, message}`
 * shape, with no stray `"details": null` field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,
    val message: String,
    val details: List<StockFailureDetailResponse>? = null,
)

/** Matches the Phase-5 doc's 409 `details` array entry shape exactly. */
data class StockFailureDetailResponse(
    val productId: UUID,
    val requested: Int,
    val available: Int,
)
