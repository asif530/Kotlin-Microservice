package com.minimart.catalog.infrastructure.persistence

import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import java.util.UUID

fun ProductDocument.toDomain(): Product = Product(
    id = UUID.fromString(id),
    name = name,
    description = description,
    category = category,
    unitPrice = unitPrice,
    stockCount = stockCount,
    status = ProductStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Product.toDocument(): ProductDocument = ProductDocument(
    id = id.toString(),
    name = name,
    description = description,
    category = category,
    unitPrice = unitPrice,
    stockCount = stockCount,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
