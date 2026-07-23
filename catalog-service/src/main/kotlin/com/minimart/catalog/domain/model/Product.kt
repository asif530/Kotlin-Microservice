package com.minimart.catalog.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The catalog-service aggregate root (CAT-001: name, description, category,
 * unit price, stock count at minimum). Deliberately a plain Kotlin data
 * class with no persistence annotations — MongoDB is an infrastructure
 * concern (see infrastructure.persistence.ProductDocument) and must never
 * leak into the domain layer.
 */
data class Product(
    val id: UUID,
    val name: String,
    val description: String,
    val category: String,
    val unitPrice: BigDecimal,
    val stockCount: Int,
    val status: ProductStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
