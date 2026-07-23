package com.minimart.order.web

import com.minimart.order.testsupport.grpc.FakeCatalogGrpcServer
import com.minimart.order.testsupport.grpc.FakeIdentityGrpcServer
import com.minimart.order.web.dto.CancelOrderResponse
import com.minimart.order.web.dto.ErrorResponse
import com.minimart.order.web.dto.OrderListResponse
import com.minimart.order.web.dto.OrderResponse
import com.minimart.order.web.dto.PlaceOrderItemRequest
import com.minimart.order.web.dto.PlaceOrderRequest
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * End-to-end slice test for Phase 5 — the riskiest integration point in
 * the system (Phase-5 doc): real Spring context, real HTTP, real
 * Postgres/Redis/RabbitMQ (Testcontainers), and real gRPC calls against
 * [FakeIdentityGrpcServer]/[FakeCatalogGrpcServer] — genuine network gRPC
 * servers standing in for identity-service/catalog-service, not Kotlin
 * interface fakes, so IdentityGrpcClientAdapter/CatalogGrpcClientAdapter's
 * actual protobuf marshalling and `runBlocking` bridge both get exercised
 * for real. Mirrors identity-service/catalog-service's own integration
 * test strategy (their own kdocs explain the RSA-keypair/token-signing
 * approach this reuses).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun bearerToken(accountId: UUID, role: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .issuer("identity-service")
            .subject(accountId.toString())
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact()
    }

    private fun headers(token: String, idempotencyKey: String? = null): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(token)
        idempotencyKey?.let { set("Idempotency-Key", it) }
    }

    private fun uniqueIdempotencyKey(label: String) = "idem-$label-${UUID.randomUUID()}"

    // ---- POST /api/orders (ORD-001..ORD-009, scenarios 16/17/18) ----------------------------

    @Test
    fun `POST orders as an eligible Customer returns 201 with the exact Phase-5 response shape`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val productA = UUID.randomUUID()
        val productB = UUID.randomUUID()
        fakeCatalogServer.seedProduct(productA, "Trail Runner 2.0", 89.99, stock = 25)
        fakeCatalogServer.seedProduct(productB, "Wireless Earbuds Pro", 129.00, stock = 40)

        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(productA, 2), PlaceOrderItemRequest(productB, 1)))
        val response = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(request, headers(bearerToken(customerId, "CUSTOMER"), uniqueIdempotencyKey("happy-path"))),
            OrderResponse::class.java,
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(customerId.toString(), body.customerId)
        assertEquals("PLACED", body.status)
        assertEquals("308.98", body.totalAmount)
        assertEquals(2, body.items.size)
        assertNotNull(body.createdAt)
        assertEquals(23, fakeCatalogServer.currentStock(productA))
        assertEquals(39, fakeCatalogServer.currentStock(productB))
    }

    @Test
    fun `POST orders for a Deactivated customer returns 403 NOT_ELIGIBLE_TO_ORDER`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markDeactivated(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)

        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1)))
        val response = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(request, headers(bearerToken(customerId, "CUSTOMER"), uniqueIdempotencyKey("deactivated"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("NOT_ELIGIBLE_TO_ORDER", response.body?.error?.code)
        assertEquals("This account cannot place an order.", response.body?.error?.message)
        assertEquals(25, fakeCatalogServer.currentStock(product)) // untouched
    }

    @Test
    fun `POST orders with insufficient stock returns 409 with the exact Phase-5 details shape`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Insulated Steel Bottle", 24.50, stock = 0)

        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 5)))
        val response = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(request, headers(bearerToken(customerId, "CUSTOMER"), uniqueIdempotencyKey("insufficient-stock"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("INSUFFICIENT_STOCK", response.body?.error?.code)
        val detail = response.body?.error?.details?.single()
        assertEquals(product, detail?.productId)
        assertEquals(5, detail?.requested)
        assertEquals(0, detail?.available)
    }

    @Test
    fun `POST orders retried with the same Idempotency-Key returns 201 with the original order, stock unchanged`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val idempotencyKey = uniqueIdempotencyKey("retry")
        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 2)))
        val token = bearerToken(customerId, "CUSTOMER")

        val first = restTemplate.exchange(
            "/api/orders", HttpMethod.POST, HttpEntity(request, headers(token, idempotencyKey)), OrderResponse::class.java,
        )
        val stockAfterFirst = fakeCatalogServer.currentStock(product)

        val second = restTemplate.exchange(
            "/api/orders", HttpMethod.POST, HttpEntity(request, headers(token, idempotencyKey)), OrderResponse::class.java,
        )

        assertEquals(HttpStatus.CREATED, second.statusCode)
        assertEquals(first.body?.id, second.body?.id)
        assertEquals(stockAfterFirst, fakeCatalogServer.currentStock(product))
    }

    @Test
    fun `POST orders with no Idempotency-Key header returns 400 VALIDATION_ERROR`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)

        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1)))
        val response = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(request, headers(bearerToken(customerId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    @Test
    fun `POST orders with no Authorization header returns 401 UNAUTHORIZED`() {
        val request = PlaceOrderRequest(listOf(PlaceOrderItemRequest(UUID.randomUUID(), 1)))
        val response = restTemplate.postForEntity(
            "/api/orders",
            HttpEntity(request, HttpHeaders().apply { set("Idempotency-Key", uniqueIdempotencyKey("no-auth")) }),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    // ---- GET /api/orders/{id} (ORD-013, scenario 19) ----------------------------------------

    @Test
    fun `GET orders id returns the order to its own customer`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val token = bearerToken(customerId, "CUSTOMER")
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))), headers(token, uniqueIdempotencyKey("get-own"))),
            OrderResponse::class.java,
        ).body!!

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}", HttpMethod.GET, HttpEntity<Void>(headers(token)), OrderResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(placed.id, response.body?.id)
    }

    @Test
    fun `GET orders id for another customer's order returns 404 ORDER_NOT_FOUND`() {
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        fakeIdentityServer.markActive(ownerId)
        fakeIdentityServer.markActive(otherId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(ownerId, "CUSTOMER"), uniqueIdempotencyKey("get-other")),
            ),
            OrderResponse::class.java,
        ).body!!

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}", HttpMethod.GET, HttpEntity<Void>(headers(bearerToken(otherId, "CUSTOMER"))), ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ORDER_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun `GET orders id as an Administrator returns any customer's order`() {
        val ownerId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        fakeIdentityServer.markActive(ownerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(ownerId, "CUSTOMER"), uniqueIdempotencyKey("get-admin")),
            ),
            OrderResponse::class.java,
        ).body!!

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}", HttpMethod.GET, HttpEntity<Void>(headers(bearerToken(adminId, "ADMIN"))), OrderResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(placed.id, response.body?.id)
    }

    @Test
    fun `GET orders id for a nonexistent id returns 404 ORDER_NOT_FOUND`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)

        val response = restTemplate.exchange(
            "/api/orders/${UUID.randomUUID()}",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(customerId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ORDER_NOT_FOUND", response.body?.error?.code)
    }

    // ---- GET /api/orders (ORD-013, scenarios 19/20) -----------------------------------------

    @Test
    fun `GET orders with no filter returns only the caller's own history`() {
        val customerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        fakeIdentityServer.markActive(otherId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(customerId, "CUSTOMER"), uniqueIdempotencyKey("list-own")),
            ),
            OrderResponse::class.java,
        )
        restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(otherId, "CUSTOMER"), uniqueIdempotencyKey("list-other")),
            ),
            OrderResponse::class.java,
        )

        val response = restTemplate.exchange(
            "/api/orders", HttpMethod.GET, HttpEntity<Void>(headers(bearerToken(customerId, "CUSTOMER"))), OrderListResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.total)
    }

    @Test
    fun `GET orders with another customer's id filter returns 403`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)

        val response = restTemplate.exchange(
            "/api/orders?customerId=${UUID.randomUUID()}",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(customerId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
    }

    @Test
    fun `GET orders as an Administrator with a customerId filter returns that customer's orders — scenario 20`() {
        val customerId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(customerId, "CUSTOMER"), uniqueIdempotencyKey("list-admin")),
            ),
            OrderResponse::class.java,
        )

        val response = restTemplate.exchange(
            "/api/orders?customerId=$customerId",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(adminId, "ADMIN"))),
            OrderListResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.total)
    }

    // ---- POST /api/orders/{id}/cancel (ORD-011/ORD-012, scenarios 21/22) --------------------

    @Test
    fun `POST orders id cancel by its own customer returns 200 with the exact Phase-6 response shape`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val token = bearerToken(customerId, "CUSTOMER")
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 2))), headers(token, uniqueIdempotencyKey("cancel-happy"))),
            OrderResponse::class.java,
        ).body!!
        assertEquals(23, fakeCatalogServer.currentStock(product))

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}/cancel", HttpMethod.POST, HttpEntity<Void>(headers(token)), CancelOrderResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(placed.id, response.body?.id)
        assertEquals("CANCELLED", response.body?.status)
        assertNotNull(response.body?.updatedAt)
        assertEquals(25, fakeCatalogServer.currentStock(product)) // ORD-012: stock restored
    }

    @Test
    fun `POST orders id cancel on an already Cancelled order returns 409 ORDER_NOT_CANCELLABLE — scenario 22a`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val token = bearerToken(customerId, "CUSTOMER")
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))), headers(token, uniqueIdempotencyKey("cancel-twice"))),
            OrderResponse::class.java,
        ).body!!
        restTemplate.exchange("/api/orders/${placed.id}/cancel", HttpMethod.POST, HttpEntity<Void>(headers(token)), CancelOrderResponse::class.java)

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}/cancel", HttpMethod.POST, HttpEntity<Void>(headers(token)), ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("ORDER_NOT_CANCELLABLE", response.body?.error?.code)
        assertEquals("This order is CANCELLED and cannot be cancelled again.", response.body?.error?.message)
    }

    @Test
    fun `POST orders id cancel for another customer's order returns 404 ORDER_NOT_FOUND — scenario 22b`() {
        val ownerId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        fakeIdentityServer.markActive(ownerId)
        fakeIdentityServer.markActive(otherId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(
                PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))),
                headers(bearerToken(ownerId, "CUSTOMER"), uniqueIdempotencyKey("cancel-other")),
            ),
            OrderResponse::class.java,
        ).body!!

        val response = restTemplate.exchange(
            "/api/orders/${placed.id}/cancel",
            HttpMethod.POST,
            HttpEntity<Void>(headers(bearerToken(otherId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ORDER_NOT_FOUND", response.body?.error?.code)
        assertEquals(24, fakeCatalogServer.currentStock(product)) // untouched by the rejected attempt
    }

    @Test
    fun `POST orders id cancel for a nonexistent id returns 404 ORDER_NOT_FOUND`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)

        val response = restTemplate.exchange(
            "/api/orders/${UUID.randomUUID()}/cancel",
            HttpMethod.POST,
            HttpEntity<Void>(headers(bearerToken(customerId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ORDER_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun `after cancellation, GET orders id and GET orders both show CANCELLED — Phase-6 regression check`() {
        val customerId = UUID.randomUUID()
        fakeIdentityServer.markActive(customerId)
        val product = UUID.randomUUID()
        fakeCatalogServer.seedProduct(product, "Trail Runner 2.0", 89.99, stock = 25)
        val token = bearerToken(customerId, "CUSTOMER")
        val placed = restTemplate.exchange(
            "/api/orders",
            HttpMethod.POST,
            HttpEntity(PlaceOrderRequest(listOf(PlaceOrderItemRequest(product, 1))), headers(token, uniqueIdempotencyKey("cancel-regression"))),
            OrderResponse::class.java,
        ).body!!

        restTemplate.exchange("/api/orders/${placed.id}/cancel", HttpMethod.POST, HttpEntity<Void>(headers(token)), CancelOrderResponse::class.java)

        val detail = restTemplate.exchange(
            "/api/orders/${placed.id}", HttpMethod.GET, HttpEntity<Void>(headers(token)), OrderResponse::class.java,
        )
        assertEquals("CANCELLED", detail.body?.status)

        val list = restTemplate.exchange("/api/orders", HttpMethod.GET, HttpEntity<Void>(headers(token)), OrderListResponse::class.java)
        assertEquals("CANCELLED", list.body?.items?.single { it.id == placed.id }?.status)
    }

    companion object {

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("orders_test")
            .withUsername("orders_test")
            .withPassword("orders_test")

        @Container
        @JvmStatic
        val rabbitMq: RabbitMQContainer = RabbitMQContainer("rabbitmq:4-management-alpine")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test-redis-password")

        private val fakeIdentityServer = FakeIdentityGrpcServer().apply { start() }
        private val fakeCatalogServer = FakeCatalogGrpcServer().apply { start() }

        private val signingKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val signingKey: PrivateKey = signingKeyPair.private

        @JvmStatic
        @AfterAll
        fun stopFakeGrpcServers() {
            fakeIdentityServer.stop()
            fakeCatalogServer.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.password") { "test-redis-password" }

            registry.add("spring.rabbitmq.host") { rabbitMq.host }
            registry.add("spring.rabbitmq.port") { rabbitMq.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbitMq.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbitMq.adminPassword }
            registry.add("spring.rabbitmq.virtual-host") { "/" }

            registry.add("order.grpc.identity-host") { "localhost" }
            registry.add("order.grpc.identity-port") { fakeIdentityServer.port }
            registry.add("order.grpc.catalog-host") { "localhost" }
            registry.add("order.grpc.catalog-port") { fakeCatalogServer.port }

            val publicKeyFile = Files.createTempDirectory("order-service-test-keys").resolve("public.pem").toFile()
            val base64 = Base64.getEncoder().encodeToString(signingKeyPair.public.encoded)
            publicKeyFile.writeText(
                buildString {
                    append("-----BEGIN PUBLIC KEY-----\n")
                    base64.chunked(64).forEach { line -> append(line).append('\n') }
                    append("-----END PUBLIC KEY-----\n")
                },
            )
            registry.add("order.security.jwt.public-key-path") { publicKeyFile.path }
        }
    }
}
