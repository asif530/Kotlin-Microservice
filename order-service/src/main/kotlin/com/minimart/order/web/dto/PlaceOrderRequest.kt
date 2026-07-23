package com.minimart.order.web.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.UUID

/**
 * POST /api/orders. ORD-002 (at least one item) is `@NotEmpty`; ORD-003
 * (quantity a whole number of at least 1) is `@Positive` plus the plain
 * `Int` type itself (a decimal JSON value like `1.5` already fails Jackson
 * deserialization before validation runs, same pattern as
 * catalog-service's `stockCount`). ORD-004 (a product at most once per
 * order) can't be expressed as a single-field annotation — see
 * OrderService for where that's actually checked.
 */
data class PlaceOrderRequest(
    @field:NotEmpty(message = "items must not be empty")
    @field:Valid
    val items: List<PlaceOrderItemRequest>,
)

data class PlaceOrderItemRequest(
    @field:NotNull(message = "productId must not be null")
    val productId: UUID,

    @field:Positive(message = "quantity must be at least 1")
    val quantity: Int,
)
