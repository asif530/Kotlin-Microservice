package com.minimart.order.web.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** ORD-002 (at least one item) and ORD-003 (quantity a whole number of at least 1). */
class PlaceOrderRequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `a request with one valid item has no violations`() {
        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(UUID.randomUUID(), 2)))
        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `an empty items list is rejected — ORD-002`() {
        val request = PlaceOrderRequest(emptyList())
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `a zero quantity is rejected — ORD-003`() {
        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(UUID.randomUUID(), 0)))
        assertTrue(validator.validate(request).isNotEmpty())
    }

    @Test
    fun `a negative quantity is rejected — ORD-003`() {
        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(UUID.randomUUID(), -1)))
        assertTrue(validator.validate(request).isNotEmpty())
    }
}
