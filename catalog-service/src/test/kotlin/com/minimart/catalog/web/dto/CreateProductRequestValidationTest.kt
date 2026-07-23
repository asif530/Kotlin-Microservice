package com.minimart.catalog.web.dto

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * CAT-001: presence of name/description/category/unitPrice/stockCount is
 * required. CAT-002 (price strictly > 0) is deliberately NOT checked here —
 * it's ProductService's job, so it can raise the Phase-3 doc's specific
 * INVALID_PRICE code instead of a generic VALIDATION_ERROR (see
 * CreateProductRequest kdoc).
 */
class CreateProductRequestValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    private fun validRequest() = CreateProductRequest(
        name = "Trail Runner 2.0",
        description = "Lightweight trail running shoe, standard fit.",
        category = "Footwear",
        unitPrice = "89.99",
        stockCount = 25,
    )

    @Test
    fun `a fully valid request has no violations`() {
        assertTrue(validator.validate(validRequest()).isEmpty())
    }

    @Test
    fun `blank name is rejected`() {
        assertTrue(validator.validate(validRequest().copy(name = "")).isNotEmpty())
    }

    @Test
    fun `blank description is rejected`() {
        assertTrue(validator.validate(validRequest().copy(description = "")).isNotEmpty())
    }

    @Test
    fun `blank category is rejected`() {
        assertTrue(validator.validate(validRequest().copy(category = "")).isNotEmpty())
    }

    @Test
    fun `blank unitPrice is rejected`() {
        assertTrue(validator.validate(validRequest().copy(unitPrice = "")).isNotEmpty())
    }

    @Test
    fun `a zero or negative unitPrice string has no bean-validation violation — that's ProductService's job`() {
        assertTrue(validator.validate(validRequest().copy(unitPrice = "-5.00")).isEmpty())
        assertTrue(validator.validate(validRequest().copy(unitPrice = "0")).isEmpty())
    }

    @Test
    fun `a negative stockCount is rejected`() {
        assertTrue(validator.validate(validRequest().copy(stockCount = -1)).isNotEmpty())
    }

    @Test
    fun `a zero stockCount is accepted — CAT-003 allows zero`() {
        assertTrue(validator.validate(validRequest().copy(stockCount = 0)).isEmpty())
    }
}
