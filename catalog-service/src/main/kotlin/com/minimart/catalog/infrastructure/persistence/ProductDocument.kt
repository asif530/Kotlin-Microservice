package com.minimart.catalog.infrastructure.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

/**
 * MongoDB document shape for the `products` collection — verbatim from
 * Archive/Development/Database §2.1 / Archive/Development/Database-Dev/mongo/00_catalog_schema.js.
 * `_id` is a client-generated UUID string (matching the seed script's
 * `10000000-0000-4000-8000-...` ids), not an ObjectId — [id] maps to `_id`
 * via [Id] regardless of field name, per Spring Data MongoDB convention.
 */
@Document(collection = "products")
data class ProductDocument(
    @Id
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val unitPrice: BigDecimal,
    val stockCount: Int,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
