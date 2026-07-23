package com.minimart.catalog.web

import com.minimart.catalog.application.ProductUseCase
import com.minimart.catalog.application.dto.CreateProductCommand
import com.minimart.catalog.application.dto.DeleteProductCommand
import com.minimart.catalog.application.dto.ListProductsCommand
import com.minimart.catalog.application.dto.UpdateProductCommand
import com.minimart.catalog.application.dto.UpdateProductStatusCommand
import com.minimart.catalog.domain.model.CallerPrincipal
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.web.dto.CreateProductRequest
import com.minimart.catalog.web.dto.CreateProductResponse
import com.minimart.catalog.web.dto.ProductDetailResponse
import com.minimart.catalog.web.dto.ProductListResponse
import com.minimart.catalog.web.dto.UpdateProductRequest
import com.minimart.catalog.web.dto.UpdateProductResponse
import com.minimart.catalog.web.dto.UpdateProductStatusRequest
import com.minimart.catalog.web.dto.UpdateProductStatusResponse
import com.minimart.catalog.web.dto.toCreateProductResponse
import com.minimart.catalog.web.dto.toProductDetailResponse
import com.minimart.catalog.web.dto.toProductSummaryResponse
import com.minimart.catalog.web.dto.toUpdateProductResponse
import com.minimart.catalog.web.dto.toUpdateProductStatusResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Phase 3/Phase 4 — Catalog foundations and management. Implements the six
 * endpoints specified in
 * Archive/Development/Backend/Phase/Phase-3-Catalog-Foundations and
 * Archive/Development/Backend/Phase/Phase-4-Catalog-Management.
 *
 * Only the admin-only mutation endpoints take a [CallerPrincipal] parameter
 * — CAT-006 makes GET/list/detail public browsing, so
 * [com.minimart.catalog.web.security.JwtAuthenticationFilter] lets GET
 * requests to /api/products through unchallenged (see that class's kdoc)
 * and never populates the request attribute
 * [com.minimart.catalog.web.security.CallerPrincipalArgumentResolver] would
 * otherwise inject.
 *
 * Kong verifies the token's signature and expiry at the gateway
 * (kong.decl.yaml's `catalog-products-create`/`catalog-product-update`/
 * `catalog-product-status`/`catalog-product-delete` routes) but never
 * role-based authorization; that gate is entirely this service's own
 * responsibility, enforced in ProductService (CAT-006), not here — this
 * controller only maps HTTP <-> the use-case boundary.
 */
@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productUseCase: ProductUseCase,
) {

    private val logger = LoggerFactory.getLogger(ProductController::class.java)

    @PostMapping
    fun createProduct(
        caller: CallerPrincipal,
        @Valid @RequestBody request: CreateProductRequest,
    ): ResponseEntity<CreateProductResponse> {
        logger.info("POST /api/products")
        val product = productUseCase.createProduct(
            CreateProductCommand(
                callerId = caller.accountId,
                callerRole = caller.role,
                name = request.name,
                description = request.description,
                category = request.category,
                unitPriceRaw = request.unitPrice,
                stockCount = request.stockCount,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(product.toCreateProductResponse())
    }

    @GetMapping
    fun listProducts(@RequestParam(required = false) category: String?): ResponseEntity<ProductListResponse> {
        logger.info("GET /api/products category={}", category)
        val products = productUseCase.listVisibleProducts(ListProductsCommand(category))
        val items = products.map { it.toProductSummaryResponse() }
        return ResponseEntity.ok(ProductListResponse(items = items, total = items.size))
    }

    @GetMapping("/{id}")
    fun getProductById(@PathVariable id: UUID): ResponseEntity<ProductDetailResponse> {
        logger.info("GET /api/products/{}", id)
        val product = productUseCase.getVisibleProduct(id)
        return ResponseEntity.ok(product.toProductDetailResponse())
    }

    @PatchMapping("/{id}")
    fun updateProduct(
        caller: CallerPrincipal,
        @PathVariable id: UUID,
        @RequestBody request: UpdateProductRequest,
    ): ResponseEntity<UpdateProductResponse> {
        logger.info("PATCH /api/products/{}", id)
        val product = productUseCase.updateProduct(
            UpdateProductCommand(
                callerId = caller.accountId,
                callerRole = caller.role,
                targetProductId = id,
                name = request.name,
                description = request.description,
                category = request.category,
                unitPriceRaw = request.unitPrice,
            ),
        )
        return ResponseEntity.ok(product.toUpdateProductResponse())
    }

    @PatchMapping("/{id}/status")
    fun updateProductStatus(
        caller: CallerPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProductStatusRequest,
    ): ResponseEntity<UpdateProductStatusResponse> {
        logger.info("PATCH /api/products/{}/status", id)
        val product = productUseCase.updateProductStatus(
            UpdateProductStatusCommand(
                callerId = caller.accountId,
                callerRole = caller.role,
                targetProductId = id,
                newStatus = ProductStatus.valueOf(request.status),
            ),
        )
        return ResponseEntity.ok(product.toUpdateProductStatusResponse())
    }

    @DeleteMapping("/{id}")
    fun deleteProduct(caller: CallerPrincipal, @PathVariable id: UUID): ResponseEntity<Void> {
        logger.info("DELETE /api/products/{}", id)
        productUseCase.deleteProduct(
            DeleteProductCommand(callerId = caller.accountId, callerRole = caller.role, targetProductId = id),
        )
        return ResponseEntity.noContent().build()
    }
}
