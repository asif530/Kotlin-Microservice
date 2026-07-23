package com.minimart.order.infrastructure.grpc

import com.minimart.catalog.grpc.CatalogServiceGrpcKt
import com.minimart.catalog.grpc.getProductRequest
import com.minimart.catalog.grpc.releaseStockRequest
import com.minimart.catalog.grpc.reserveStockRequest
import com.minimart.order.domain.port.CatalogClientPort
import com.minimart.order.domain.port.ProductSnapshot
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * gRPC client adapter for catalog-service's `GetProduct`/`ReserveStock`/
 * `ReleaseStock` (ORD-005/ORD-007/ORD-008/ORD-012, CAT-011). Bridges into
 * the generated coroutine stub via `runBlocking` — see
 * IdentityGrpcClientAdapter's kdoc for why (same reasoning applies here).
 */
@Component
class CatalogGrpcClientAdapter(
    @Qualifier("catalogGrpcChannel") channel: ManagedChannel,
) : CatalogClientPort {

    private val logger = LoggerFactory.getLogger(CatalogGrpcClientAdapter::class.java)
    private val stub = CatalogServiceGrpcKt.CatalogServiceCoroutineStub(channel)

    override fun getProduct(productId: UUID): ProductSnapshot? = runBlocking {
        try {
            val response = stub.getProduct(getProductRequest { this.productId = productId.toString() })
            ProductSnapshot(
                productId = UUID.fromString(response.productId),
                name = response.name,
                // BigDecimal.valueOf (not the BigDecimal(Double) constructor) round-trips via
                // Double.toString() rather than the double's exact binary representation —
                // catalog.proto's `price` field is a `double` (an existing, already-shared wire
                // contract this adapter doesn't get to unilaterally change), so this is the
                // correct, non-lossy-for-typical-currency-values way to convert it back to the
                // BigDecimal the rest of this domain uses.
                unitPrice = BigDecimal.valueOf(response.price),
                stockAvailable = response.stockAvailable,
            )
        } catch (notFound: StatusException) {
            if (notFound.status.code == Status.Code.NOT_FOUND) {
                null
            } else {
                logger.warn("GetProduct call to catalog-service failed unexpectedly: {}", notFound.message)
                throw notFound
            }
        }
    }

    override fun reserveStock(productId: UUID, quantity: Int): Boolean = runBlocking {
        stub.reserveStock(reserveStockRequest { this.productId = productId.toString(); this.quantity = quantity }).success
    }

    override fun releaseStock(productId: UUID, quantity: Int): Boolean = runBlocking {
        stub.releaseStock(releaseStockRequest { this.productId = productId.toString(); this.quantity = quantity }).success
    }
}
