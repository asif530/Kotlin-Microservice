package com.minimart.catalog.web.dto

import com.minimart.catalog.domain.model.Product

/** Maps the domain Product to Phase-3/Phase-4's response DTOs. Kept out of the domain/application layers on purpose. */

fun Product.toCreateProductResponse(): CreateProductResponse = CreateProductResponse(
    id = id.toString(),
    name = name,
    description = description,
    category = category,
    unitPrice = unitPrice.toPlainString(),
    stockCount = stockCount,
    status = status.name,
    createdAt = createdAt.toString(),
)

fun Product.toProductSummaryResponse(): ProductSummaryResponse = ProductSummaryResponse(
    id = id.toString(),
    name = name,
    category = category,
    unitPrice = unitPrice.toPlainString(),
    inStock = stockCount > 0,
)

fun Product.toProductDetailResponse(): ProductDetailResponse = ProductDetailResponse(
    id = id.toString(),
    name = name,
    description = description,
    category = category,
    unitPrice = unitPrice.toPlainString(),
    inStock = stockCount > 0,
)

fun Product.toUpdateProductResponse(): UpdateProductResponse = UpdateProductResponse(
    id = id.toString(),
    name = name,
    unitPrice = unitPrice.toPlainString(),
    stockCount = stockCount,
    status = status.name,
    updatedAt = updatedAt.toString(),
)

fun Product.toUpdateProductStatusResponse(): UpdateProductStatusResponse = UpdateProductStatusResponse(
    id = id.toString(),
    status = status.name,
    updatedAt = updatedAt.toString(),
)
