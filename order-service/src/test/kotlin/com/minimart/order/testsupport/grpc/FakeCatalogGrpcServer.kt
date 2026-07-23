package com.minimart.order.testsupport.grpc

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
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A real gRPC server standing in for catalog-service — see
 * FakeIdentityGrpcServer's kdoc for why this is a real network server, not
 * a plain Kotlin interface fake. Mirrors CatalogGrpcService's own
 * semantics (NOT_FOUND for missing/invisible products; atomic-ish
 * reserve/release, synchronized per-product since this is single-process
 * test infrastructure, not a real database).
 */
class FakeCatalogGrpcServer : CatalogServiceGrpcKt.CatalogServiceCoroutineImplBase() {

    private class ProductState(var name: String, var priceCents: Long, var stock: Int, var visible: Boolean)

    private val products = ConcurrentHashMap<UUID, ProductState>()
    private var server: Server? = null

    val port: Int get() = requireNotNull(server) { "Server not started" }.port

    fun seedProduct(productId: UUID, name: String, price: Double, stock: Int, visible: Boolean = true) {
        products[productId] = ProductState(name, Math.round(price * 100), stock, visible)
    }

    fun currentStock(productId: UUID): Int? = products[productId]?.stock

    fun start() {
        server = ServerBuilder.forPort(0).addService(this).build().start()
    }

    fun stop() {
        server?.shutdownNow()
    }

    override suspend fun getProduct(request: GetProductRequest): ProductResponse {
        val productId = UUID.fromString(request.productId)
        val product = products[productId]?.takeIf { it.visible }
            ?: throw StatusException(Status.NOT_FOUND.withDescription("No product with this id"))
        return productResponse {
            this.productId = productId.toString()
            name = product.name
            price = product.priceCents / 100.0
            stockAvailable = product.stock
        }
    }

    override suspend fun reserveStock(request: ReserveStockRequest): ReserveStockResponse {
        val productId = UUID.fromString(request.productId)
        val reserved = synchronized(this) {
            val product = products[productId]
            if (product != null && product.visible && product.stock >= request.quantity) {
                product.stock -= request.quantity
                true
            } else {
                false
            }
        }
        return reserveStockResponse {
            success = reserved
            message = if (reserved) "Reserved" else "Insufficient stock"
        }
    }

    override suspend fun releaseStock(request: ReleaseStockRequest): ReleaseStockResponse {
        val productId = UUID.fromString(request.productId)
        val released = synchronized(this) {
            val product = products[productId]
            if (product != null && product.visible) {
                product.stock += request.quantity
                true
            } else {
                false
            }
        }
        return releaseStockResponse {
            success = released
            message = if (released) "Released" else "Product not found"
        }
    }
}
