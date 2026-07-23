package com.minimart.catalog.infrastructure.grpc

import com.minimart.catalog.application.testsupport.FakeProductRepository
import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.grpc.getProductRequest
import com.minimart.catalog.grpc.releaseStockRequest
import com.minimart.catalog.grpc.reserveStockRequest
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Pure unit test for Phase-5's gRPC server (catalog.proto's GetProduct/ReserveStock/ReleaseStock). */
class CatalogGrpcServiceTest {

    private lateinit var productRepository: FakeProductRepository
    private lateinit var service: CatalogGrpcService

    @BeforeEach
    fun setUp() {
        productRepository = FakeProductRepository()
        service = CatalogGrpcService(productRepository)
    }

    private fun seedProduct(stockCount: Int = 25, status: ProductStatus = ProductStatus.ACTIVE, unitPrice: String = "89.99"): Product {
        val now = Instant.now()
        return productRepository.insert(
            Product(
                id = UUID.randomUUID(),
                name = "Trail Runner 2.0",
                description = "test fixture",
                category = "Footwear",
                unitPrice = BigDecimal(unitPrice),
                stockCount = stockCount,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    // ---- getProduct ---------------------------------------------------------------------------

    @Test
    fun `getProduct returns name price and stock for a visible product`() = runBlocking {
        val product = seedProduct(stockCount = 25, unitPrice = "89.99")

        val response = service.getProduct(getProductRequest { productId = product.id.toString() })

        assertEquals(product.id.toString(), response.productId)
        assertEquals("Trail Runner 2.0", response.name)
        assertEquals(89.99, response.price, 0.001)
        assertEquals(25, response.stockAvailable)
    }

    @Test
    fun `getProduct throws NOT_FOUND for a Deactivated product — same as nonexistent, CAT-008`() {
        val deactivated = seedProduct(status = ProductStatus.DEACTIVATED)

        val exception = assertThrows(StatusException::class.java) {
            runBlocking { service.getProduct(getProductRequest { productId = deactivated.id.toString() }) }
        }
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun `getProduct throws NOT_FOUND for a nonexistent id`() {
        val exception = assertThrows(StatusException::class.java) {
            runBlocking { service.getProduct(getProductRequest { productId = UUID.randomUUID().toString() }) }
        }
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    // ---- reserveStock (ORD-007/ORD-008, CAT-011) ----------------------------------------------

    @Test
    fun `reserveStock succeeds and decrements stock when enough is available`() = runBlocking {
        val product = seedProduct(stockCount = 25)

        val response = service.reserveStock(reserveStockRequest { productId = product.id.toString(); quantity = 2 })

        assertTrue(response.success)
        assertEquals(23, productRepository.findById(product.id)?.stockCount)
    }

    @Test
    fun `reserveStock fails without changing stock when quantity exceeds availability — GEN-001`() = runBlocking {
        val product = seedProduct(stockCount = 1)

        val response = service.reserveStock(reserveStockRequest { productId = product.id.toString(); quantity = 5 })

        assertFalse(response.success)
        assertEquals(1, productRepository.findById(product.id)?.stockCount)
    }

    @Test
    fun `reserveStock fails for a Deactivated product`() = runBlocking {
        val deactivated = seedProduct(stockCount = 25, status = ProductStatus.DEACTIVATED)

        val response = service.reserveStock(reserveStockRequest { productId = deactivated.id.toString(); quantity = 1 })

        assertFalse(response.success)
    }

    @Test
    fun `reserveStock fails for a nonexistent product`() = runBlocking {
        val response = service.reserveStock(reserveStockRequest { productId = UUID.randomUUID().toString(); quantity = 1 })

        assertFalse(response.success)
    }

    // ---- releaseStock (ORD-012, this phase's compensating action) -----------------------------

    @Test
    fun `releaseStock succeeds and increments stock back`() = runBlocking {
        val product = seedProduct(stockCount = 23)

        val response = service.releaseStock(releaseStockRequest { productId = product.id.toString(); quantity = 2 })

        assertTrue(response.success)
        assertEquals(25, productRepository.findById(product.id)?.stockCount)
    }

    @Test
    fun `releaseStock fails for a nonexistent product`() = runBlocking {
        val response = service.releaseStock(releaseStockRequest { productId = UUID.randomUUID().toString(); quantity = 1 })

        assertFalse(response.success)
    }
}
