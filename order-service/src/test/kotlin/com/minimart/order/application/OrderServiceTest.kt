package com.minimart.order.application

import com.minimart.order.application.dto.CancelOrderCommand
import com.minimart.order.application.dto.GetOrderCommand
import com.minimart.order.application.dto.ListOrdersCommand
import com.minimart.order.application.dto.PlaceOrderCommand
import com.minimart.order.application.dto.PlaceOrderLineItem
import com.minimart.order.application.testsupport.FakeCatalogClientPort
import com.minimart.order.application.testsupport.FakeIdempotencyPort
import com.minimart.order.application.testsupport.FakeIdentityClientPort
import com.minimart.order.application.testsupport.FakeOrderEventPublisherPort
import com.minimart.order.application.testsupport.FakeOrderRepository
import com.minimart.order.domain.exception.ForbiddenActionException
import com.minimart.order.domain.exception.InsufficientStockException
import com.minimart.order.domain.exception.NotEligibleToOrderException
import com.minimart.order.domain.exception.OrderNotCancellableException
import com.minimart.order.domain.exception.OrderNotFoundException
import com.minimart.order.domain.exception.OrderValidationException
import com.minimart.order.domain.model.Order
import com.minimart.order.domain.model.OrderItem
import com.minimart.order.domain.model.OrderStatus
import com.minimart.order.domain.model.RoleCode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Pure unit tests for the Phase-5 use-case interactor — no Spring context, no database, no real gRPC/Redis/RabbitMQ. */
class OrderServiceTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var identityClient: FakeIdentityClientPort
    private lateinit var catalogClient: FakeCatalogClientPort
    private lateinit var idempotencyPort: FakeIdempotencyPort
    private lateinit var eventPublisher: FakeOrderEventPublisherPort
    private lateinit var orderService: OrderService

    private val customerId = UUID.randomUUID()
    private val otherCustomerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val productA = UUID.randomUUID()
    private val productB = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        orderRepository = FakeOrderRepository()
        identityClient = FakeIdentityClientPort()
        catalogClient = FakeCatalogClientPort()
        idempotencyPort = FakeIdempotencyPort()
        eventPublisher = FakeOrderEventPublisherPort()
        orderService = OrderService(orderRepository, identityClient, catalogClient, idempotencyPort, eventPublisher, SimpleMeterRegistry())

        identityClient.markEligible(customerId)
        identityClient.markEligible(otherCustomerId)
        catalogClient.seedProduct(productA, "Trail Runner 2.0", "89.99", stock = 25)
        catalogClient.seedProduct(productB, "Wireless Earbuds Pro", "129.00", stock = 40)
    }

    private fun placeCommand(
        customerId: UUID = this.customerId,
        idempotencyKey: String = "idem-${UUID.randomUUID()}",
        items: List<PlaceOrderLineItem> = listOf(PlaceOrderLineItem(productA, 2), PlaceOrderLineItem(productB, 1)),
    ) = PlaceOrderCommand(customerId, idempotencyKey, items)

    // ---- placeOrder happy path (ORD-001..ORD-009, scenario 16) ------------------------------

    @Test
    fun `placeOrder computes totalAmount as the sum of captured unit price times quantity — ORD-006`() {
        val order = orderService.placeOrder(placeCommand())

        assertEquals(BigDecimal("308.98"), order.totalAmount)
        assertEquals(OrderStatus.PLACED, order.status)
        assertEquals(2, order.items.size)
    }

    @Test
    fun `placeOrder captures product name and price at placement time — ORD-005`() {
        val order = orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2))))

        val item = order.items.single()
        assertEquals("Trail Runner 2.0", item.productNameSnapshot)
        assertEquals(BigDecimal("89.99"), item.unitPriceSnapshot)
        assertEquals(BigDecimal("179.98"), item.lineTotal)
    }

    @Test
    fun `placeOrder decrements stock by the ordered quantity exactly once — ORD-008`() {
        orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2))))

        assertEquals(23, catalogClient.currentStock(productA))
    }

    @Test
    fun `placeOrder publishes order-placed after the order is persisted`() {
        val order = orderService.placeOrder(placeCommand())

        assertEquals(1, eventPublisher.published.size)
        assertEquals(order.id, eventPublisher.published.single().id)
    }

    @Test
    fun `placeOrder remembers the idempotency key for future retries`() {
        val order = orderService.placeOrder(placeCommand(idempotencyKey = "idem-remember-test"))

        assertEquals(order.id, idempotencyPort.findOrderId("idem-remember-test"))
    }

    // ---- placeOrder validation (ORD-002/ORD-004) -------------------------------------------

    @Test
    fun `placeOrder with a duplicate product id throws OrderValidation — ORD-004`() {
        assertThrows(OrderValidationException::class.java) {
            orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 1), PlaceOrderLineItem(productA, 2))))
        }
    }

    // ---- placeOrder eligibility (ORD-001, scenario related to Deactivated accounts) --------

    @Test
    fun `placeOrder for an ineligible customer throws NotEligibleToOrder before touching catalog`() {
        val ineligibleCustomerId = UUID.randomUUID() // never marked eligible

        assertThrows(NotEligibleToOrderException::class.java) {
            orderService.placeOrder(placeCommand(customerId = ineligibleCustomerId))
        }

        // Stock must be untouched — eligibility is checked before any catalog call.
        assertEquals(25, catalogClient.currentStock(productA))
        assertEquals(40, catalogClient.currentStock(productB))
    }

    // ---- placeOrder insufficient stock + compensation (ORD-007/GEN-001, scenario 17) -------

    @Test
    fun `placeOrder with insufficient stock throws InsufficientStock with the failing item's details`() {
        val exception = assertThrows(InsufficientStockException::class.java) {
            orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 999))))
        }

        val detail = exception.details.single()
        assertEquals(productA, detail.productId)
        assertEquals(999, detail.requested)
        assertEquals(25, detail.available)
    }

    @Test
    fun `placeOrder releases an already-succeeded reservation when a later item in the same order fails`() {
        // productA has plenty of stock and would succeed; productB is requested in an
        // impossible quantity and must fail — the whole order is rejected, and productA's
        // reservation must be given back (no partial deduction survives a rejected order).
        assertThrows(InsufficientStockException::class.java) {
            orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2), PlaceOrderLineItem(productB, 999))))
        }

        assertEquals(25, catalogClient.currentStock(productA))
        assertEquals(40, catalogClient.currentStock(productB))
    }

    @Test
    fun `placeOrder with a Deactivated or nonexistent product reports it as zero available — Phase-5 regression note`() {
        val deactivatedProduct = UUID.randomUUID()
        catalogClient.seedProduct(deactivatedProduct, "Discontinued Tent Stakes", "12.00", stock = 5, visible = false)

        val exception = assertThrows(InsufficientStockException::class.java) {
            orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(deactivatedProduct, 1))))
        }

        assertEquals(0, exception.details.single().available)
    }

    @Test
    fun `placeOrder does not create an order when reservation fails`() {
        assertThrows(InsufficientStockException::class.java) {
            orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 999))))
        }

        assertTrue(orderRepository.findSummaries(customerId).isEmpty())
    }

    // ---- placeOrder idempotency (ORD-009, scenario 18) -------------------------------------

    @Test
    fun `placeOrder with a previously used idempotency key returns the same order without re-reserving stock`() {
        val firstOrder = orderService.placeOrder(placeCommand(idempotencyKey = "idem-retry-test", items = listOf(PlaceOrderLineItem(productA, 2))))
        val stockAfterFirst = catalogClient.currentStock(productA)

        val secondOrder = orderService.placeOrder(placeCommand(idempotencyKey = "idem-retry-test", items = listOf(PlaceOrderLineItem(productA, 2))))

        assertEquals(firstOrder.id, secondOrder.id)
        assertEquals(stockAfterFirst, catalogClient.currentStock(productA))
        assertEquals(1, eventPublisher.published.size) // not re-published on replay
    }

    @Test
    fun `placeOrder releases this attempt's reservation when it loses an idempotency-key race at the database`() {
        // Simulates the winning concurrent request having already committed its order directly
        // in Postgres — bypassing OrderService/idempotencyPort entirely, exactly as a genuine
        // race would leave the *losing* request's own idempotency-cache check with nothing to
        // find (both requests check Redis before either has written to it; only the database's
        // UNIQUE constraint actually catches the collision).
        val now = Instant.now()
        val winningOrder = Order(
            id = UUID.randomUUID(),
            customerId = customerId,
            status = OrderStatus.PLACED,
            totalAmount = BigDecimal("179.98"),
            idempotencyKey = "idem-race-test",
            items = listOf(OrderItem(UUID.randomUUID(), productA, "Trail Runner 2.0", BigDecimal("89.99"), 2)),
            createdAt = now,
            updatedAt = now,
        )
        orderRepository.insert(winningOrder)
        catalogClient.seedProduct(productA, "Trail Runner 2.0", "89.99", stock = 23) // winner's reservation already applied
        orderRepository.forceRaceOnNextInsert = true

        val losingAttemptResult = orderService.placeOrder(
            PlaceOrderCommand(customerId, "idem-race-test", listOf(PlaceOrderLineItem(productA, 3))),
        )

        assertEquals(winningOrder.id, losingAttemptResult.id)
        // The losing attempt's own (3-unit) reservation must have been released, leaving stock
        // exactly where the winning attempt's reservation left it.
        assertEquals(23, catalogClient.currentStock(productA))
    }

    // ---- getOrder (ORD-013, scenario 19) ----------------------------------------------------

    @Test
    fun `getOrder returns the order to its own customer`() {
        val placed = orderService.placeOrder(placeCommand())

        val fetched = orderService.getOrder(GetOrderCommand(customerId, RoleCode.CUSTOMER, placed.id))

        assertEquals(placed.id, fetched.id)
    }

    @Test
    fun `getOrder returns any order to an Administrator`() {
        val placed = orderService.placeOrder(placeCommand())

        val fetched = orderService.getOrder(GetOrderCommand(adminId, RoleCode.ADMIN, placed.id))

        assertEquals(placed.id, fetched.id)
    }

    @Test
    fun `getOrder for another customer's order throws OrderNotFound — not Forbidden, ORD-013`() {
        val placed = orderService.placeOrder(placeCommand(customerId = customerId))

        assertThrows(OrderNotFoundException::class.java) {
            orderService.getOrder(GetOrderCommand(otherCustomerId, RoleCode.CUSTOMER, placed.id))
        }
    }

    @Test
    fun `getOrder for a nonexistent id throws OrderNotFound`() {
        assertThrows(OrderNotFoundException::class.java) {
            orderService.getOrder(GetOrderCommand(customerId, RoleCode.CUSTOMER, UUID.randomUUID()))
        }
    }

    // ---- listOrders (ORD-013, scenarios 19/20) ---------------------------------------------

    @Test
    fun `listOrders with no filter returns only the caller's own history`() {
        orderService.placeOrder(placeCommand(customerId = customerId))
        orderService.placeOrder(placeCommand(customerId = otherCustomerId))

        val summaries = orderService.listOrders(ListOrdersCommand(customerId, RoleCode.CUSTOMER, null))

        assertEquals(1, summaries.size)
    }

    @Test
    fun `listOrders with the caller's own customerId filter is allowed`() {
        orderService.placeOrder(placeCommand(customerId = customerId))

        val summaries = orderService.listOrders(ListOrdersCommand(customerId, RoleCode.CUSTOMER, customerId))

        assertEquals(1, summaries.size)
    }

    @Test
    fun `listOrders with another customer's id filter throws Forbidden`() {
        assertThrows(ForbiddenActionException::class.java) {
            orderService.listOrders(ListOrdersCommand(customerId, RoleCode.CUSTOMER, otherCustomerId))
        }
    }

    @Test
    fun `listOrders as an Administrator with a customerId filter returns that customer's orders — scenario 20`() {
        orderService.placeOrder(placeCommand(customerId = customerId))
        orderService.placeOrder(placeCommand(customerId = otherCustomerId))

        val summaries = orderService.listOrders(ListOrdersCommand(adminId, RoleCode.ADMIN, otherCustomerId))

        assertEquals(1, summaries.size)
    }

    @Test
    fun `listOrders as an Administrator with no filter returns every customer's orders`() {
        orderService.placeOrder(placeCommand(customerId = customerId))
        orderService.placeOrder(placeCommand(customerId = otherCustomerId))

        val summaries = orderService.listOrders(ListOrdersCommand(adminId, RoleCode.ADMIN, null))

        assertEquals(2, summaries.size)
    }

    // ---- cancelOrder (ORD-011/ORD-012, scenarios 21/22) -------------------------------------

    @Test
    fun `cancelOrder by its own customer sets status to CANCELLED`() {
        val placed = orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2))))

        val cancelled = orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        assertEquals(OrderStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancelOrder restores the ordered quantities back to available stock — ORD-012`() {
        val placed = orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2), PlaceOrderLineItem(productB, 1))))
        assertEquals(23, catalogClient.currentStock(productA))
        assertEquals(39, catalogClient.currentStock(productB))

        orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        assertEquals(25, catalogClient.currentStock(productA))
        assertEquals(40, catalogClient.currentStock(productB))
    }

    @Test
    fun `cancelOrder publishes order-cancelled after the status change is persisted`() {
        val placed = orderService.placeOrder(placeCommand())

        val cancelled = orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        assertEquals(1, eventPublisher.cancelled.size)
        assertEquals(cancelled.id, eventPublisher.cancelled.single().id)
    }

    @Test
    fun `cancelOrder never touches customerId, totalAmount, idempotencyKey, or line items — ORD-005-ORD-014`() {
        val placed = orderService.placeOrder(placeCommand())

        val cancelled = orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        assertEquals(placed.customerId, cancelled.customerId)
        assertEquals(placed.totalAmount, cancelled.totalAmount)
        assertEquals(placed.idempotencyKey, cancelled.idempotencyKey)
        assertEquals(placed.items, cancelled.items)
    }

    @Test
    fun `cancelOrder on an already Cancelled order throws OrderNotCancellable with the exact Phase-6 message — scenario 22a`() {
        val placed = orderService.placeOrder(placeCommand())
        orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        val exception = assertThrows(OrderNotCancellableException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))
        }

        assertEquals("This order is CANCELLED and cannot be cancelled again.", exception.message)
    }

    @Test
    fun `cancelOrder does not release stock a second time when cancelling an already Cancelled order fails`() {
        val placed = orderService.placeOrder(placeCommand(items = listOf(PlaceOrderLineItem(productA, 2))))
        orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))
        assertEquals(25, catalogClient.currentStock(productA))

        assertThrows(OrderNotCancellableException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))
        }

        assertEquals(25, catalogClient.currentStock(productA)) // unchanged, not double-released
    }

    @Test
    fun `cancelOrder for another customer's order throws OrderNotFound — not Forbidden, scenario 22b`() {
        val placed = orderService.placeOrder(placeCommand(customerId = customerId))

        assertThrows(OrderNotFoundException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(otherCustomerId, placed.id))
        }
    }

    @Test
    fun `cancelOrder for another customer's order does not release its stock`() {
        val placed = orderService.placeOrder(placeCommand(customerId = customerId, items = listOf(PlaceOrderLineItem(productA, 2))))

        assertThrows(OrderNotFoundException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(otherCustomerId, placed.id))
        }

        assertEquals(23, catalogClient.currentStock(productA))
    }

    @Test
    fun `cancelOrder for a nonexistent id throws OrderNotFound`() {
        assertThrows(OrderNotFoundException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(customerId, UUID.randomUUID()))
        }
    }

    @Test
    fun `an Administrator cancelling another customer's order still throws OrderNotFound — ORD-011 has no admin override`() {
        val placed = orderService.placeOrder(placeCommand(customerId = customerId))

        assertThrows(OrderNotFoundException::class.java) {
            orderService.cancelOrder(CancelOrderCommand(adminId, placed.id))
        }
    }

    @Test
    fun `cancelling then listing shows CANCELLED everywhere — Phase-6 regression check`() {
        val placed = orderService.placeOrder(placeCommand())

        orderService.cancelOrder(CancelOrderCommand(customerId, placed.id))

        val fetched = orderService.getOrder(GetOrderCommand(customerId, RoleCode.CUSTOMER, placed.id))
        assertEquals(OrderStatus.CANCELLED, fetched.status)

        val summary = orderService.listOrders(ListOrdersCommand(customerId, RoleCode.CUSTOMER, null)).single()
        assertEquals(OrderStatus.CANCELLED, summary.status)
    }
}
