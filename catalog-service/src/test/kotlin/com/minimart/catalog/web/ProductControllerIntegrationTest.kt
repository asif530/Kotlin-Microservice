package com.minimart.catalog.web

import com.minimart.catalog.infrastructure.persistence.ProductDocument
import com.minimart.catalog.web.dto.CreateProductRequest
import com.minimart.catalog.web.dto.CreateProductResponse
import com.minimart.catalog.web.dto.ErrorResponse
import com.minimart.catalog.web.dto.ProductDetailResponse
import com.minimart.catalog.web.dto.ProductListResponse
import com.minimart.catalog.web.dto.UpdateProductRequest
import com.minimart.catalog.web.dto.UpdateProductResponse
import com.minimart.catalog.web.dto.UpdateProductStatusRequest
import com.minimart.catalog.web.dto.UpdateProductStatusResponse
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * End-to-end slice test for Phase 3/Phase 4: real Spring context, real
 * HTTP, real MongoDB (Testcontainers) — mirrors identity-service's
 * UserControllerIntegrationTest strategy. catalog-service has no TokenIssuer
 * of its own (it only verifies), so this test generates its own RSA
 * keypair, points `catalog.security.jwt.public-key-path` at the exported
 * public half, and signs tokens directly with the private half — the same
 * approach [com.minimart.catalog.infrastructure.security.JwtTokenVerifierAdapterTest]
 * uses at the unit level, now exercised through real HTTP + a real filter.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    private fun bearerToken(role: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact()
    }

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply { setBearerAuth(token) }

    private fun <T : Any> patchWithAuth(path: String, token: String, body: Any, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.PATCH, HttpEntity(body, authHeaders(token)), responseType)

    private fun <T : Any> deleteWithAuth(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.DELETE, HttpEntity<Void>(authHeaders(token)), responseType)

    private fun insertProduct(
        name: String = "Insulated Steel Bottle",
        category: String = "Home & Kitchen",
        unitPrice: String = "24.50",
        stockCount: Int = 0,
        status: String = "ACTIVE",
    ): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        mongoTemplate.save(
            ProductDocument(
                id = id.toString(),
                name = name,
                description = "test fixture product",
                category = category,
                unitPrice = BigDecimal(unitPrice),
                stockCount = stockCount,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    // ---- POST /api/products (CAT-001..CAT-004, CAT-006, scenario 11) ------------------------

    @Test
    fun `POST products as an Administrator returns 201 with the exact Phase-3 response shape`() {
        val request = CreateProductRequest(
            name = "Trail Runner 2.0",
            description = "Lightweight trail running shoe, standard fit.",
            category = "Footwear",
            unitPrice = "89.99",
            stockCount = 25,
        )

        val response = restTemplate.exchange(
            "/api/products",
            HttpMethod.POST,
            HttpEntity(request, authHeaders(bearerToken("ADMIN"))),
            CreateProductResponse::class.java,
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals("Trail Runner 2.0", body.name)
        assertEquals("89.99", body.unitPrice)
        assertEquals(25, body.stockCount)
        assertEquals("ACTIVE", body.status)
        assertNotNull(body.createdAt)
    }

    @Test
    fun `POST products with a zero price returns 400 INVALID_PRICE with the exact Phase-3 body`() {
        val request = CreateProductRequest(
            name = "Trail Runner 2.0",
            description = "Lightweight trail running shoe, standard fit.",
            category = "Footwear",
            unitPrice = "0",
            stockCount = 25,
        )

        val response = restTemplate.exchange(
            "/api/products",
            HttpMethod.POST,
            HttpEntity(request, authHeaders(bearerToken("ADMIN"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_PRICE", response.body?.error?.code)
        assertEquals("unitPrice must be greater than zero.", response.body?.error?.message)
    }

    @Test
    fun `POST products as a Customer returns 403 with the exact Phase-3 body`() {
        val request = CreateProductRequest(
            name = "Trail Runner 2.0",
            description = "Lightweight trail running shoe, standard fit.",
            category = "Footwear",
            unitPrice = "89.99",
            stockCount = 25,
        )

        val response = restTemplate.exchange(
            "/api/products",
            HttpMethod.POST,
            HttpEntity(request, authHeaders(bearerToken("CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("Only an Administrator can create a product.", response.body?.error?.message)
    }

    @Test
    fun `POST products with no Authorization header returns 401 UNAUTHORIZED`() {
        val request = CreateProductRequest("Trail Runner 2.0", "desc", "Footwear", "89.99", 25)

        val response = restTemplate.postForEntity("/api/products", HttpEntity(request), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    @Test
    fun `POST products with a blank name returns 400 VALIDATION_ERROR`() {
        val request = CreateProductRequest("", "desc", "Footwear", "89.99", 25)

        val response = restTemplate.exchange(
            "/api/products",
            HttpMethod.POST,
            HttpEntity(request, authHeaders(bearerToken("ADMIN"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    // ---- GET /api/products (CAT-006/CAT-007/CAT-008, scenario 12) ---------------------------

    @Test
    fun `GET products requires no Authorization header and includes a zero-stock item marked out of stock`() {
        insertProduct(name = "Insulated Steel Bottle", category = "Kitchenware", stockCount = 0)

        val response = restTemplate.getForEntity("/api/products?category=Kitchenware", ProductListResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(1, body.total)
        assertEquals(false, body.items.single().inStock)
    }

    @Test
    fun `GET products never includes a Deactivated product`() {
        insertProduct(name = "Discontinued Tent Stakes", category = "Outdoor", stockCount = 5, status = "DEACTIVATED")

        val response = restTemplate.getForEntity("/api/products?category=Outdoor", ProductListResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.total)
    }

    // ---- GET /api/products/{id} (CAT-006/CAT-007/CAT-008, scenario 12) ----------------------

    @Test
    fun `GET products id for an ACTIVE zero-stock product returns 200 with inStock false`() {
        val id = insertProduct(stockCount = 0)

        val response = restTemplate.getForEntity("/api/products/$id", ProductDetailResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(id.toString(), response.body?.id)
        assertEquals(false, response.body?.inStock)
    }

    @Test
    fun `GET products id for a Deactivated product returns 404 with the exact Phase-3 body`() {
        val id = insertProduct(status = "DEACTIVATED")

        val response = restTemplate.getForEntity("/api/products/$id", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("PRODUCT_NOT_FOUND", response.body?.error?.code)
        assertEquals("No product with this id.", response.body?.error?.message)
    }

    @Test
    fun `GET products id for a nonexistent id returns 404 PRODUCT_NOT_FOUND`() {
        val response = restTemplate.getForEntity("/api/products/${UUID.randomUUID()}", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("PRODUCT_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun `GET products id with a malformed id path segment returns 400 VALIDATION_ERROR`() {
        val response = restTemplate.getForEntity("/api/products/not-a-uuid", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    // ---- PATCH /api/products/{id} (CAT-010, CAT-006, scenario 13) ---------------------------

    @Test
    fun `PATCH products id as an Administrator returns 200 with the exact Phase-4 response shape`() {
        val id = insertProduct(name = "Trail Runner 2.0", unitPrice = "89.99", stockCount = 25)

        val response = patchWithAuth(
            "/api/products/$id",
            bearerToken("ADMIN"),
            UpdateProductRequest(unitPrice = "84.99"),
            UpdateProductResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(id.toString(), body.id)
        assertEquals("Trail Runner 2.0", body.name)
        assertEquals("84.99", body.unitPrice)
        assertEquals(25, body.stockCount)
        assertEquals("ACTIVE", body.status)
        assertNotNull(body.updatedAt)
    }

    @Test
    fun `PATCH products id as a Customer returns 403`() {
        val id = insertProduct()

        val response = patchWithAuth(
            "/api/products/$id",
            bearerToken("CUSTOMER"),
            UpdateProductRequest(unitPrice = "84.99"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("Only an Administrator can update a product.", response.body?.error?.message)
    }

    @Test
    fun `PATCH products id with a zero price returns 400 INVALID_PRICE`() {
        val id = insertProduct()

        val response = patchWithAuth(
            "/api/products/$id",
            bearerToken("ADMIN"),
            UpdateProductRequest(unitPrice = "0"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_PRICE", response.body?.error?.code)
    }

    @Test
    fun `PATCH products id for a nonexistent id returns 404 PRODUCT_NOT_FOUND`() {
        val response = patchWithAuth(
            "/api/products/${UUID.randomUUID()}",
            bearerToken("ADMIN"),
            UpdateProductRequest(unitPrice = "84.99"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("PRODUCT_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun `GET products id immediately reflects a PATCH — Phase-3 regression check`() {
        val id = insertProduct(name = "Trail Runner 2.0", unitPrice = "89.99")

        patchWithAuth("/api/products/$id", bearerToken("ADMIN"), UpdateProductRequest(unitPrice = "84.99"), UpdateProductResponse::class.java)

        val response = restTemplate.getForEntity("/api/products/$id", ProductDetailResponse::class.java)
        assertEquals("84.99", response.body?.unitPrice)
    }

    // ---- PATCH /api/products/{id}/status (CAT-008, CAT-006, scenario 14) --------------------

    @Test
    fun `PATCH products id status deactivate returns 200 with the exact Phase-4 response shape`() {
        val id = insertProduct()

        val response = patchWithAuth(
            "/api/products/$id/status",
            bearerToken("ADMIN"),
            UpdateProductStatusRequest("DEACTIVATED"),
            UpdateProductStatusResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(id.toString(), response.body?.id)
        assertEquals("DEACTIVATED", response.body?.status)
        assertNotNull(response.body?.updatedAt)
    }

    @Test
    fun `PATCH products id status as a Customer returns 403`() {
        val id = insertProduct()

        val response = patchWithAuth(
            "/api/products/$id/status",
            bearerToken("CUSTOMER"),
            UpdateProductStatusRequest("DEACTIVATED"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Only an Administrator can change a product's status.", response.body?.error?.message)
    }

    @Test
    fun `PATCH products id status with an invalid value returns 400 VALIDATION_ERROR`() {
        val id = insertProduct()

        val response = patchWithAuth(
            "/api/products/$id/status",
            bearerToken("ADMIN"),
            UpdateProductStatusRequest("ARCHIVED"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    @Test
    fun `after deactivation, GET products id returns 404 and GET products excludes it — the Phase-4 regression check`() {
        val id = insertProduct(name = "Discontinued Tent Stakes", category = "OutdoorGear")

        patchWithAuth(
            "/api/products/$id/status",
            bearerToken("ADMIN"),
            UpdateProductStatusRequest("DEACTIVATED"),
            UpdateProductStatusResponse::class.java,
        )

        val detailResponse = restTemplate.getForEntity("/api/products/$id", ErrorResponse::class.java)
        assertEquals(HttpStatus.NOT_FOUND, detailResponse.statusCode)
        assertEquals("PRODUCT_NOT_FOUND", detailResponse.body?.error?.code)

        val listResponse = restTemplate.getForEntity("/api/products?category=OutdoorGear", ProductListResponse::class.java)
        assertEquals(0, listResponse.body?.total)
    }

    // ---- DELETE /api/products/{id} (CAT-009, CAT-006, scenario 15) --------------------------

    @Test
    fun `DELETE products id with no order history returns 204`() {
        val id = insertProduct()

        val response = deleteWithAuth("/api/products/$id", bearerToken("ADMIN"), Void::class.java)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, restTemplate.getForEntity("/api/products/$id", ErrorResponse::class.java).statusCode)
    }

    @Test
    fun `DELETE products id as a Customer returns 403`() {
        val id = insertProduct()

        val response = deleteWithAuth("/api/products/$id", bearerToken("CUSTOMER"), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Only an Administrator can delete a product.", response.body?.error?.message)
    }

    @Test
    fun `DELETE products id for a nonexistent id returns 404 PRODUCT_NOT_FOUND`() {
        val response = deleteWithAuth("/api/products/${UUID.randomUUID()}", bearerToken("ADMIN"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("PRODUCT_NOT_FOUND", response.body?.error?.code)
    }

    companion object {

        @Container
        @JvmStatic
        val mongoDb: MongoDBContainer = MongoDBContainer("mongo:8")

        private val signingKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val signingKey: PrivateKey = signingKeyPair.private

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            // spring.mongodb (not spring.data.mongodb) — see application.yml's comment on the
            // same key for why: Spring Boot 4 renamed MongoProperties' binding prefix.
            registry.add("spring.mongodb.uri") { mongoDb.getReplicaSetUrl("catalog_test") }

            val publicKeyFile = Files.createTempDirectory("catalog-service-test-keys").resolve("public.pem").toFile()
            val base64 = Base64.getEncoder().encodeToString(signingKeyPair.public.encoded)
            publicKeyFile.writeText(
                buildString {
                    append("-----BEGIN PUBLIC KEY-----\n")
                    base64.chunked(64).forEach { line -> append(line).append('\n') }
                    append("-----END PUBLIC KEY-----\n")
                },
            )
            registry.add("catalog.security.jwt.public-key-path") { publicKeyFile.path }
        }
    }
}
