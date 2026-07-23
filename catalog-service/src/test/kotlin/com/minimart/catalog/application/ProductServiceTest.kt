package com.minimart.catalog.application

import com.minimart.catalog.application.dto.CreateProductCommand
import com.minimart.catalog.application.dto.DeleteProductCommand
import com.minimart.catalog.application.dto.ListProductsCommand
import com.minimart.catalog.application.dto.UpdateProductCommand
import com.minimart.catalog.application.dto.UpdateProductStatusCommand
import com.minimart.catalog.application.testsupport.FakeOrderHistoryPort
import com.minimart.catalog.application.testsupport.FakeProductRepository
import com.minimart.catalog.domain.exception.ForbiddenActionException
import com.minimart.catalog.domain.exception.InvalidPriceException
import com.minimart.catalog.domain.exception.ProductHasOrderHistoryException
import com.minimart.catalog.domain.exception.ProductNotFoundException
import com.minimart.catalog.domain.exception.ProductValidationException
import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.domain.model.RoleCode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Pure unit tests for the Phase-3/Phase-4 use-case interactor — no Spring context, no database. */
class ProductServiceTest {

    private lateinit var productRepository: FakeProductRepository
    private lateinit var orderHistoryPort: FakeOrderHistoryPort
    private lateinit var productService: ProductService

    private val adminId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        productRepository = FakeProductRepository()
        orderHistoryPort = FakeOrderHistoryPort()
        productService = ProductService(productRepository, orderHistoryPort, SimpleMeterRegistry())
    }

    private fun seedProduct(
        name: String = "Trail Runner 2.0",
        category: String = "Footwear",
        unitPrice: String = "89.99",
        stockCount: Int = 25,
        status: ProductStatus = ProductStatus.ACTIVE,
    ): Product {
        val now = Instant.now()
        val product = Product(
            id = UUID.randomUUID(),
            name = name,
            description = "Lightweight trail running shoe, standard fit.",
            category = category,
            unitPrice = BigDecimal(unitPrice),
            stockCount = stockCount,
            status = status,
            createdAt = now,
            updatedAt = now,
        )
        return productRepository.insert(product)
    }

    private fun createCommand(
        callerId: UUID = adminId,
        callerRole: RoleCode = RoleCode.ADMIN,
        name: String = "Trail Runner 2.0",
        description: String = "Lightweight trail running shoe, standard fit.",
        category: String = "Footwear",
        unitPriceRaw: String = "89.99",
        stockCount: Int = 25,
    ) = CreateProductCommand(callerId, callerRole, name, description, category, unitPriceRaw, stockCount)

    // ---- createProduct (CAT-001..CAT-004, CAT-006, scenario 11) -----------------------------

    @Test
    fun `createProduct as an Administrator persists an ACTIVE product with the given fields`() {
        val product = productService.createProduct(createCommand())

        assertEquals("Trail Runner 2.0", product.name)
        assertEquals("Footwear", product.category)
        assertEquals(BigDecimal("89.99"), product.unitPrice)
        assertEquals(25, product.stockCount)
        assertEquals(ProductStatus.ACTIVE, product.status)
    }

    @Test
    fun `createProduct as a Customer throws Forbidden with the exact Phase-3 message`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            productService.createProduct(createCommand(callerId = customerId, callerRole = RoleCode.CUSTOMER))
        }
        assertEquals("Only an Administrator can create a product.", exception.message)
    }

    @Test
    fun `createProduct with a zero price throws InvalidPrice with the exact Phase-3 message`() {
        val exception = assertThrows(InvalidPriceException::class.java) {
            productService.createProduct(createCommand(unitPriceRaw = "0"))
        }
        assertEquals("unitPrice must be greater than zero.", exception.message)
    }

    @Test
    fun `createProduct with a negative price throws InvalidPrice with the exact Phase-3 message`() {
        val exception = assertThrows(InvalidPriceException::class.java) {
            productService.createProduct(createCommand(unitPriceRaw = "-5.00"))
        }
        assertEquals("unitPrice must be greater than zero.", exception.message)
    }

    @Test
    fun `createProduct with a non-numeric price throws InvalidPrice`() {
        assertThrows(InvalidPriceException::class.java) {
            productService.createProduct(createCommand(unitPriceRaw = "not-a-number"))
        }
    }

    @Test
    fun `createProduct as a Customer with an invalid price still throws Forbidden — role is checked first`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            productService.createProduct(
                createCommand(callerId = customerId, callerRole = RoleCode.CUSTOMER, unitPriceRaw = "-5.00"),
            )
        }
        assertEquals("Only an Administrator can create a product.", exception.message)
    }

    // ---- listVisibleProducts (CAT-006/CAT-007/CAT-008, scenario 12) -------------------------

    @Test
    fun `listVisibleProducts includes a zero-stock product, marked visible by the caller via inStock later`() {
        seedProduct(name = "Insulated Steel Bottle", category = "Home & Kitchen", stockCount = 0)

        val products = productService.listVisibleProducts(ListProductsCommand(category = null))

        assertEquals(1, products.size)
        assertEquals(0, products.single().stockCount)
    }

    @Test
    fun `listVisibleProducts excludes a Deactivated product regardless of its stock`() {
        seedProduct(name = "Discontinued Tent Stakes", category = "Outdoor", stockCount = 5, status = ProductStatus.DEACTIVATED)
        seedProduct(name = "Wireless Earbuds Pro", category = "Electronics", stockCount = 40)

        val products = productService.listVisibleProducts(ListProductsCommand(category = null))

        assertEquals(1, products.size)
        assertEquals("Wireless Earbuds Pro", products.single().name)
    }

    @Test
    fun `listVisibleProducts filters by category when given`() {
        seedProduct(name = "Trail Runner 2.0", category = "Footwear")
        seedProduct(name = "Wireless Earbuds Pro", category = "Electronics")

        val products = productService.listVisibleProducts(ListProductsCommand(category = "Footwear"))

        assertEquals(1, products.size)
        assertEquals("Footwear", products.single().category)
    }

    @Test
    fun `listVisibleProducts returns two distinct records sharing the same name — CAT-005`() {
        seedProduct(name = "Trail Runner 2.0", category = "Footwear", unitPrice = "89.99")
        seedProduct(name = "Trail Runner 2.0", category = "Footwear", unitPrice = "94.99")

        val products = productService.listVisibleProducts(ListProductsCommand(category = "Footwear"))

        assertEquals(2, products.size)
        assertTrue(products.all { it.name == "Trail Runner 2.0" })
    }

    // ---- getVisibleProduct (CAT-006/CAT-007/CAT-008, scenario 12) ---------------------------

    @Test
    fun `getVisibleProduct returns an ACTIVE product even at zero stock`() {
        val seeded = seedProduct(name = "Insulated Steel Bottle", category = "Home & Kitchen", stockCount = 0)

        val product = productService.getVisibleProduct(seeded.id)

        assertEquals(seeded.id, product.id)
        assertEquals(0, product.stockCount)
    }

    @Test
    fun `getVisibleProduct throws ProductNotFound for a nonexistent id`() {
        assertThrows(ProductNotFoundException::class.java) {
            productService.getVisibleProduct(UUID.randomUUID())
        }
    }

    @Test
    fun `getVisibleProduct throws ProductNotFound for a Deactivated product — indistinguishable from missing, CAT-008`() {
        val deactivated = seedProduct(status = ProductStatus.DEACTIVATED)

        assertThrows(ProductNotFoundException::class.java) {
            productService.getVisibleProduct(deactivated.id)
        }
    }

    // ---- updateProduct (CAT-010, CAT-006, scenario 13) --------------------------------------

    @Test
    fun `updateProduct as an Administrator changes only the given fields, other fields unchanged`() {
        val seeded = seedProduct()

        val updated = productService.updateProduct(
            UpdateProductCommand(adminId, RoleCode.ADMIN, seeded.id, name = null, description = null, category = null, unitPriceRaw = "84.99"),
        )

        assertEquals(BigDecimal("84.99"), updated.unitPrice)
        assertEquals(seeded.name, updated.name)
        assertEquals(seeded.category, updated.category)
        assertEquals(seeded.description, updated.description)
        assertEquals(seeded.stockCount, updated.stockCount)
    }

    @Test
    fun `updateProduct can change name, description, and category together`() {
        val seeded = seedProduct()

        val updated = productService.updateProduct(
            UpdateProductCommand(
                adminId,
                RoleCode.ADMIN,
                seeded.id,
                name = "Trail Runner 3.0",
                description = "Updated description.",
                category = "Athletic Footwear",
                unitPriceRaw = null,
            ),
        )

        assertEquals("Trail Runner 3.0", updated.name)
        assertEquals("Updated description.", updated.description)
        assertEquals("Athletic Footwear", updated.category)
        assertEquals(seeded.unitPrice, updated.unitPrice)
    }

    @Test
    fun `updateProduct as a Customer throws Forbidden`() {
        val seeded = seedProduct()

        val exception = assertThrows(ForbiddenActionException::class.java) {
            productService.updateProduct(
                UpdateProductCommand(customerId, RoleCode.CUSTOMER, seeded.id, null, null, null, "84.99"),
            )
        }
        assertEquals("Only an Administrator can update a product.", exception.message)
    }

    @Test
    fun `updateProduct with a zero price throws InvalidPrice`() {
        val seeded = seedProduct()

        assertThrows(InvalidPriceException::class.java) {
            productService.updateProduct(UpdateProductCommand(adminId, RoleCode.ADMIN, seeded.id, null, null, null, "0"))
        }
    }

    @Test
    fun `updateProduct with a blank name throws ProductValidation`() {
        val seeded = seedProduct()

        assertThrows(ProductValidationException::class.java) {
            productService.updateProduct(UpdateProductCommand(adminId, RoleCode.ADMIN, seeded.id, "", null, null, null))
        }
    }

    @Test
    fun `updateProduct on a Deactivated product still succeeds — admin edits aren't gated by visibility`() {
        val deactivated = seedProduct(status = ProductStatus.DEACTIVATED)

        val updated = productService.updateProduct(
            UpdateProductCommand(adminId, RoleCode.ADMIN, deactivated.id, null, null, null, "50.00"),
        )

        assertEquals(BigDecimal("50.00"), updated.unitPrice)
        assertEquals(ProductStatus.DEACTIVATED, updated.status)
    }

    @Test
    fun `updateProduct throws ProductNotFound for a nonexistent id`() {
        assertThrows(ProductNotFoundException::class.java) {
            productService.updateProduct(UpdateProductCommand(adminId, RoleCode.ADMIN, UUID.randomUUID(), null, null, null, "50.00"))
        }
    }

    @Test
    fun `updateProduct as a Customer checks role before existence — Forbidden even for a nonexistent id`() {
        assertThrows(ForbiddenActionException::class.java) {
            productService.updateProduct(UpdateProductCommand(customerId, RoleCode.CUSTOMER, UUID.randomUUID(), null, null, null, "50.00"))
        }
    }

    // ---- updateProductStatus (CAT-008, CAT-006, scenario 14) --------------------------------

    @Test
    fun `updateProductStatus lets an Administrator deactivate an ACTIVE product`() {
        val seeded = seedProduct()

        val updated = productService.updateProductStatus(
            UpdateProductStatusCommand(adminId, RoleCode.ADMIN, seeded.id, ProductStatus.DEACTIVATED),
        )

        assertEquals(ProductStatus.DEACTIVATED, updated.status)
    }

    @Test
    fun `updateProductStatus lets an Administrator reactivate a Deactivated product`() {
        val deactivated = seedProduct(status = ProductStatus.DEACTIVATED)

        val updated = productService.updateProductStatus(
            UpdateProductStatusCommand(adminId, RoleCode.ADMIN, deactivated.id, ProductStatus.ACTIVE),
        )

        assertEquals(ProductStatus.ACTIVE, updated.status)
    }

    @Test
    fun `updateProductStatus as a Customer throws Forbidden`() {
        val seeded = seedProduct()

        val exception = assertThrows(ForbiddenActionException::class.java) {
            productService.updateProductStatus(
                UpdateProductStatusCommand(customerId, RoleCode.CUSTOMER, seeded.id, ProductStatus.DEACTIVATED),
            )
        }
        assertEquals("Only an Administrator can change a product's status.", exception.message)
    }

    @Test
    fun `updateProductStatus throws ProductNotFound for a nonexistent id`() {
        assertThrows(ProductNotFoundException::class.java) {
            productService.updateProductStatus(
                UpdateProductStatusCommand(adminId, RoleCode.ADMIN, UUID.randomUUID(), ProductStatus.DEACTIVATED),
            )
        }
    }

    @Test
    fun `deactivating then browsing shows the regression Phase-3 requires — findVisibleById now returns null`() {
        val seeded = seedProduct()

        productService.updateProductStatus(UpdateProductStatusCommand(adminId, RoleCode.ADMIN, seeded.id, ProductStatus.DEACTIVATED))

        assertThrows(ProductNotFoundException::class.java) { productService.getVisibleProduct(seeded.id) }
        assertTrue(productService.listVisibleProducts(ListProductsCommand(category = null)).none { it.id == seeded.id })
    }

    // ---- deleteProduct (CAT-009, CAT-006, scenario 15) --------------------------------------

    @Test
    fun `deleteProduct removes a product with no order history`() {
        val seeded = seedProduct()

        productService.deleteProduct(DeleteProductCommand(adminId, RoleCode.ADMIN, seeded.id))

        assertNull(productRepository.findById(seeded.id))
    }

    @Test
    fun `deleteProduct on a product with order history throws ProductHasOrderHistory with the exact Phase-4 message`() {
        val seeded = seedProduct()
        orderHistoryPort.markAsOrdered(seeded.id)

        val exception = assertThrows(ProductHasOrderHistoryException::class.java) {
            productService.deleteProduct(DeleteProductCommand(adminId, RoleCode.ADMIN, seeded.id))
        }

        assertEquals(
            "This product has been part of at least one order and can only be deactivated, not deleted.",
            exception.message,
        )
        assertEquals(seeded.id, productRepository.findById(seeded.id)?.id)
    }

    @Test
    fun `deleteProduct as a Customer throws Forbidden and does not delete`() {
        val seeded = seedProduct()

        assertThrows(ForbiddenActionException::class.java) {
            productService.deleteProduct(DeleteProductCommand(customerId, RoleCode.CUSTOMER, seeded.id))
        }
        assertEquals(seeded.id, productRepository.findById(seeded.id)?.id)
    }

    @Test
    fun `deleteProduct throws ProductNotFound for a nonexistent id`() {
        assertThrows(ProductNotFoundException::class.java) {
            productService.deleteProduct(DeleteProductCommand(adminId, RoleCode.ADMIN, UUID.randomUUID()))
        }
    }

    @Test
    fun `deleteProduct on a Deactivated product with no order history still succeeds`() {
        val deactivated = seedProduct(status = ProductStatus.DEACTIVATED)

        productService.deleteProduct(DeleteProductCommand(adminId, RoleCode.ADMIN, deactivated.id))

        assertNull(productRepository.findById(deactivated.id))
    }
}
