package com.minimart.catalog.application

import com.minimart.catalog.application.dto.CreateProductCommand
import com.minimart.catalog.application.dto.DeleteProductCommand
import com.minimart.catalog.application.dto.ListProductsCommand
import com.minimart.catalog.application.dto.UpdateProductCommand
import com.minimart.catalog.application.dto.UpdateProductStatusCommand
import com.minimart.catalog.domain.model.Product
import java.util.UUID

/**
 * Inbound port (use-case boundary) for Phase-3/Phase-4's catalog endpoints.
 * The web layer (ProductController) depends on this interface, not on the
 * concrete ProductService, mirroring identity-service's
 * AuthUseCase/UserAccountUseCase split.
 */
interface ProductUseCase {

    /**
     * CAT-001..CAT-004: creates a product with the required fields.
     * CAT-006: admin only.
     *
     * @throws com.minimart.catalog.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.catalog.domain.exception.InvalidPriceException
     *   if `unitPrice` is not a positive decimal number (CAT-002).
     */
    fun createProduct(command: CreateProductCommand): Product

    /**
     * CAT-006/CAT-007/CAT-008: public browsing/search. Only ACTIVE products
     * are ever returned, zero-stock products included.
     */
    fun listVisibleProducts(command: ListProductsCommand): List<Product>

    /**
     * CAT-006/CAT-007/CAT-008: public single-product view. Only an ACTIVE
     * product is ever returned.
     *
     * @throws com.minimart.catalog.domain.exception.ProductNotFoundException
     *   if no ACTIVE product exists with the given id (missing or
     *   Deactivated — indistinguishable to the caller, per CAT-008).
     */
    fun getVisibleProduct(id: UUID): Product

    /**
     * CAT-010: an Administrator can change a product's price/name/
     * description/category — a partial update, only the fields present in
     * [command] are changed. This never affects orders already placed
     * (ORD-005's snapshot). CAT-006: admin only.
     *
     * @throws com.minimart.catalog.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.catalog.domain.exception.ProductNotFoundException
     *   if no product exists with the given id.
     * @throws com.minimart.catalog.domain.exception.InvalidPriceException
     *   if `unitPrice` is present but not a positive decimal number (CAT-002).
     * @throws com.minimart.catalog.domain.exception.ProductValidationException
     *   if `name`/`description`/`category` is present but blank.
     */
    fun updateProduct(command: UpdateProductCommand): Product

    /**
     * CAT-008: an Administrator can deactivate or reactivate a product —
     * same endpoint handles both directions, mirroring identity-service's
     * ACC-008 status endpoint. CAT-006: admin only.
     *
     * @throws com.minimart.catalog.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.catalog.domain.exception.ProductNotFoundException
     *   if no product exists with the given id.
     */
    fun updateProductStatus(command: UpdateProductStatusCommand): Product

    /**
     * CAT-009: permanently deletes a product that has never been part of a
     * placed order; a product with order history can only be Deactivated
     * (PATCH .../status), never deleted. CAT-006: admin only.
     *
     * @throws com.minimart.catalog.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.catalog.domain.exception.ProductNotFoundException
     *   if no product exists with the given id.
     * @throws com.minimart.catalog.domain.exception.ProductHasOrderHistoryException
     *   if the product has been part of at least one placed order.
     */
    fun deleteProduct(command: DeleteProductCommand)
}
