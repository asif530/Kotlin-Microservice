package com.minimart.catalog.infrastructure.grpc

import com.minimart.catalog.domain.port.ProductRepositoryPort
import com.minimart.catalog.grpc.CatalogServiceGrpcKt
import com.minimart.catalog.grpc.GetProductRequest
import com.minimart.catalog.grpc.ProductResponse
import com.minimart.catalog.grpc.ReleaseStockRequest
import com.minimart.catalog.grpc.ReleaseStockResponse
import com.minimart.catalog.grpc.ReserveStockRequest
import com.minimart.catalog.grpc.ReserveStockResponse
import com.minimart.catalog.grpc.productResponse
import com.minimart.catalog.grpc.releaseStockResponse
import com.minimart.catalog.grpc.reserveStockResponse
import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * gRPC server implementation of `CatalogService` (catalog.proto) — Phase-5's
 * cross-service reads/writes: order-service calls this per line item during
 * checkout (GetProduct then ReserveStock, ORD-005/ORD-007/ORD-008) and on
 * cancellation or checkout rollback (ReleaseStock, ORD-012 / this phase's
 * own compensating-action requirement).
 *
 * [GetProductRequest]/[ReserveStockRequest] both use
 * [ProductRepositoryPort]'s visible-only (ACTIVE) lookups — CAT-008: a
 * Deactivated product is treated as not found here exactly as it is on the
 * public REST surface, which is what lets order-service collapse
 * "Deactivated" and "nonexistent" and "genuinely out of stock" into the
 * same INSUFFICIENT_STOCK outcome for a customer (Phase-5 doc's regression
 * note).
 */
@Component
class CatalogGrpcService(
    private val productRepository: ProductRepositoryPort,
) : CatalogServiceGrpcKt.CatalogServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(CatalogGrpcService::class.java)

    override suspend fun getProduct(request: GetProductRequest): ProductResponse {
        val productId = parseProductId(request.productId)

        val product = productRepository.findVisibleById(productId) ?: run {
            logger.info("GetProduct: no visible product for id={}", productId)
            throw StatusException(Status.NOT_FOUND.withDescription("No product with this id"))
        }

        return productResponse {
            this.productId = product.id.toString()
            name = product.name
            price = product.unitPrice.toDouble()
            stockAvailable = product.stockCount
        }
    }

    override suspend fun reserveStock(request: ReserveStockRequest): ReserveStockResponse {
        val productId = parseProductId(request.productId)

        val reserved = productRepository.reserveStock(productId, request.quantity)
        if (!reserved) {
            logger.info("ReserveStock failed: id={} quantity={}", productId, request.quantity)
        }
        return reserveStockResponse {
            success = reserved
            message = if (reserved) "Reserved" else "Insufficient stock or product not found/inactive"
        }
    }

    override suspend fun releaseStock(request: ReleaseStockRequest): ReleaseStockResponse {
        val productId = parseProductId(request.productId)

        val released = productRepository.releaseStock(productId, request.quantity)
        if (!released) {
            logger.warn("ReleaseStock found no matching ACTIVE product: id={} quantity={}", productId, request.quantity)
        }
        return releaseStockResponse {
            success = released
            message = if (released) "Released" else "Product not found or inactive"
        }
    }

    private fun parseProductId(raw: String): UUID = try {
        UUID.fromString(raw)
    } catch (notAUuid: IllegalArgumentException) {
        throw StatusException(Status.INVALID_ARGUMENT.withDescription("product_id is not a valid UUID"))
    }
}
