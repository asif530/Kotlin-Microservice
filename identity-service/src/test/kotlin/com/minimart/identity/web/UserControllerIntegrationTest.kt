package com.minimart.identity.web

import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import com.minimart.identity.domain.port.TokenIssuer
import com.minimart.identity.infrastructure.security.RsaKeyPairProvider
import com.minimart.identity.web.dto.ErrorResponse
import com.minimart.identity.web.dto.UpdateProfileRequest
import com.minimart.identity.web.dto.UpdateProfileResponse
import com.minimart.identity.web.dto.UpdateRoleRequest
import com.minimart.identity.web.dto.UpdateRoleResponse
import com.minimart.identity.web.dto.UpdateStatusRequest
import com.minimart.identity.web.dto.UpdateStatusResponse
import com.minimart.identity.web.dto.UserProfileResponse
import io.jsonwebtoken.Jwts
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * End-to-end slice test for Phase 2: real Spring context, real HTTP, real
 * Postgres (Testcontainers), covering every 200/401/403/400/404 path across
 * all five endpoints — mirrors AuthControllerIntegrationTest's Phase-1
 * strategy. Test accounts are inserted directly via JdbcTemplate (their
 * password/hash is irrelevant here — Phase-2 never exercises login) and
 * tokens are minted with the real [TokenIssuer] bean, so every verification
 * in [com.minimart.identity.infrastructure.security.JwtTokenVerifierAdapter]
 * runs against genuinely signed tokens, the same as production.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UserControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenIssuer: TokenIssuer

    @Autowired
    private lateinit var rsaKeyPairProvider: RsaKeyPairProvider

    private fun uniqueEmail(localPart: String): String = "$localPart+${UUID.randomUUID()}@example.test"

    private fun insertAccount(fullName: String, roleCode: String, status: String = "ACTIVE"): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO accounts (id, email, password_hash, full_name, role_id, status)
            VALUES (?, ?, ?, ?, (SELECT id FROM roles WHERE code = ?), ?)
            """.trimIndent(),
            id,
            uniqueEmail(fullName.lowercase().replace(" ", ".")),
            "irrelevant-hash-not-used-by-phase-2",
            fullName,
            roleCode,
            status,
        )
        return id
    }

    private fun bearerToken(accountId: UUID, roleCode: String): String {
        val now = Instant.now()
        val dummyAccount = Account(
            id = accountId,
            email = "unused@example.test",
            passwordHash = "unused",
            fullName = "unused",
            role = RoleCode.fromDbCode(roleCode),
            status = AccountStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        return tokenIssuer.issue(dummyAccount).token
    }

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply { setBearerAuth(token) }

    private fun <T : Any> getWithAuth(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.GET, HttpEntity<Void>(authHeaders(token)), responseType)

    private fun <T : Any> patchWithAuth(path: String, token: String, body: Any, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.PATCH, HttpEntity(body, authHeaders(token)), responseType)

    // ---- GET /api/users/me (ACC-011, scenario 4) ----------------------------------------------

    @Test
    fun `GET users me returns 200 with the exact Phase-2 response shape`() {
        val customerId = insertAccount("Alice Nguyen", "CUSTOMER")
        val token = bearerToken(customerId, "CUSTOMER")

        val response = getWithAuth("/api/users/me", token, UserProfileResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(customerId.toString(), body.id)
        assertEquals("Alice Nguyen", body.fullName)
        assertEquals("CUSTOMER", body.role)
        assertEquals("ACTIVE", body.status)
        assertNotNull(body.createdAt)
    }

    @Test
    fun `GET users me with no Authorization header returns 401 UNAUTHORIZED`() {
        val response = restTemplate.getForEntity("/api/users/me", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    @Test
    fun `GET users me with a malformed bearer token returns 401 UNAUTHORIZED`() {
        val response = getWithAuth("/api/users/me", "not-a-real-jwt", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    @Test
    fun `GET users me with an expired token returns 401 UNAUTHORIZED`() {
        val expiredToken = Jwts.builder()
            .issuer("identity-service")
            .subject(UUID.randomUUID().toString())
            .claim("role", "CUSTOMER")
            .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
            .expiration(Date.from(Instant.now().minusSeconds(3600)))
            .signWith(rsaKeyPairProvider.privateKey, Jwts.SIG.RS256)
            .compact()

        val response = getWithAuth("/api/users/me", expiredToken, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    // ---- PATCH /api/users/me (ACC-011, scenario 5) --------------------------------------------

    @Test
    fun `PATCH users me returns 200 with updated fullName and updatedAt, other fields unchanged`() {
        val customerId = insertAccount("Bob Baker", "CUSTOMER")
        val token = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth(
            "/api/users/me",
            token,
            UpdateProfileRequest("Bob N. Baker"),
            UpdateProfileResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(customerId.toString(), body.id)
        assertEquals("Bob N. Baker", body.fullName)
        assertEquals("CUSTOMER", body.role)
        assertEquals("ACTIVE", body.status)
        assertNotNull(body.updatedAt)
    }

    @Test
    fun `PATCH users me with a blank fullName returns 400 VALIDATION_ERROR`() {
        val customerId = insertAccount("Carla Diaz", "CUSTOMER")
        val token = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth("/api/users/me", token, UpdateProfileRequest(""), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    // ---- GET /api/users/{id} (ACC-011, scenario 6, admin only) ---------------------------------

    @Test
    fun `GET users id as an Administrator returns 200 with the target account`() {
        val adminId = insertAccount("Root Admin", "ADMIN")
        val targetId = insertAccount("Chen Park", "CUSTOMER")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = getWithAuth("/api/users/$targetId", adminToken, UserProfileResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(targetId.toString(), response.body?.id)
        assertEquals("Chen Park", response.body?.fullName)
    }

    @Test
    fun `GET users id as a Customer viewing another account returns 403 with the exact Phase-2 body`() {
        val customerId = insertAccount("Dana Lee", "CUSTOMER")
        val otherId = insertAccount("Erin Shah", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = getWithAuth("/api/users/$otherId", customerToken, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("You can only view your own account.", response.body?.error?.message)
    }

    @Test
    fun `GET users id as a Customer viewing their own id also returns 403 — self-view goes through me`() {
        val customerId = insertAccount("Frank Osei", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = getWithAuth("/api/users/$customerId", customerToken, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("You can only view your own account.", response.body?.error?.message)
    }

    @Test
    fun `GET users id as an Administrator for a nonexistent id returns 404 ACCOUNT_NOT_FOUND`() {
        val adminId = insertAccount("Root Admin Two", "ADMIN")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = getWithAuth("/api/users/${UUID.randomUUID()}", adminToken, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ACCOUNT_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun `GET users id with a malformed id path segment returns 400 VALIDATION_ERROR`() {
        val adminId = insertAccount("Root Admin Three", "ADMIN")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = getWithAuth("/api/users/not-a-uuid", adminToken, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    // ---- PATCH /api/users/{id}/status (ACC-008, scenarios 7/8, admin only) --------------------

    @Test
    fun `PATCH users id status deactivate then reactivate both return 200, role untouched`() {
        val adminId = insertAccount("Root Admin Four", "ADMIN")
        val targetId = insertAccount("Gina Ruiz", "CUSTOMER")
        val adminToken = bearerToken(adminId, "ADMIN")

        val deactivateResponse = patchWithAuth(
            "/api/users/$targetId/status",
            adminToken,
            UpdateStatusRequest("DEACTIVATED"),
            UpdateStatusResponse::class.java,
        )
        assertEquals(HttpStatus.OK, deactivateResponse.statusCode)
        assertEquals("DEACTIVATED", deactivateResponse.body?.status)
        assertEquals(targetId.toString(), deactivateResponse.body?.id)

        val reactivateResponse = patchWithAuth(
            "/api/users/$targetId/status",
            adminToken,
            UpdateStatusRequest("ACTIVE"),
            UpdateStatusResponse::class.java,
        )
        assertEquals(HttpStatus.OK, reactivateResponse.statusCode)
        assertEquals("ACTIVE", reactivateResponse.body?.status)

        // role untouched by a status change (ACC-008)
        val roleAfter = jdbcTemplate.queryForObject(
            "SELECT r.code FROM accounts a JOIN roles r ON r.id = a.role_id WHERE a.id = ?",
            String::class.java,
            targetId,
        )
        assertEquals("CUSTOMER", roleAfter)
    }

    @Test
    fun `PATCH users id status as a Customer acting on another account returns 403 with the exact Phase-2 body`() {
        val customerId = insertAccount("Henry Todd", "CUSTOMER")
        val targetId = insertAccount("Ivy Brooks", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth(
            "/api/users/$targetId/status",
            customerToken,
            UpdateStatusRequest("DEACTIVATED"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("Only an Administrator can change an account's status.", response.body?.error?.message)
    }

    @Test
    fun `PATCH users id status as a Customer acting on their own account also returns 403 — ACC-008`() {
        val customerId = insertAccount("Jack Nolan", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth(
            "/api/users/$customerId/status",
            customerToken,
            UpdateStatusRequest("DEACTIVATED"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Only an Administrator can change an account's status.", response.body?.error?.message)
    }

    @Test
    fun `PATCH users id status with an invalid status value returns 400 VALIDATION_ERROR`() {
        val adminId = insertAccount("Root Admin Five", "ADMIN")
        val targetId = insertAccount("Kim Novak", "CUSTOMER")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = patchWithAuth(
            "/api/users/$targetId/status",
            adminToken,
            UpdateStatusRequest("SUSPENDED"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    @Test
    fun `PATCH users id status for a nonexistent id as an Administrator returns 404 ACCOUNT_NOT_FOUND`() {
        val adminId = insertAccount("Root Admin Six", "ADMIN")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = patchWithAuth(
            "/api/users/${UUID.randomUUID()}/status",
            adminToken,
            UpdateStatusRequest("ACTIVE"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ACCOUNT_NOT_FOUND", response.body?.error?.code)
    }

    // ---- PATCH /api/users/{id}/role (ACC-009, scenario 9, admin only) -------------------------

    @Test
    fun `PATCH users id role as an Administrator promotes a Customer and returns 200`() {
        val adminId = insertAccount("Root Admin Seven", "ADMIN")
        val targetId = insertAccount("Liam Cho", "CUSTOMER")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = patchWithAuth(
            "/api/users/$targetId/role",
            adminToken,
            UpdateRoleRequest("ADMIN"),
            UpdateRoleResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(targetId.toString(), response.body?.id)
        assertEquals("ADMIN", response.body?.role)
    }

    @Test
    fun `PATCH users id role as a Customer acting on another account returns 403 with the exact Phase-2 body`() {
        val customerId = insertAccount("Mona Reyes", "CUSTOMER")
        val targetId = insertAccount("Noah Park", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth(
            "/api/users/$targetId/role",
            customerToken,
            UpdateRoleRequest("ADMIN"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
        assertEquals("Only an Administrator can grant the Administrator role.", response.body?.error?.message)
    }

    @Test
    fun `PATCH users id role as a Customer attempting self-promotion returns 403 — no self-service path per ACC-009`() {
        val customerId = insertAccount("Omar Siddiqui", "CUSTOMER")
        val customerToken = bearerToken(customerId, "CUSTOMER")

        val response = patchWithAuth(
            "/api/users/$customerId/role",
            customerToken,
            UpdateRoleRequest("ADMIN"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Only an Administrator can grant the Administrator role.", response.body?.error?.message)
    }

    @Test
    fun `PATCH users id role with a CUSTOMER value returns 400 VALIDATION_ERROR — no demotion path is defined`() {
        val adminId = insertAccount("Root Admin Eight", "ADMIN")
        val targetId = insertAccount("Priya Nair", "ADMIN")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = patchWithAuth(
            "/api/users/$targetId/role",
            adminToken,
            UpdateRoleRequest("CUSTOMER"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    @Test
    fun `PATCH users id role for a nonexistent id as an Administrator returns 404 ACCOUNT_NOT_FOUND`() {
        val adminId = insertAccount("Root Admin Nine", "ADMIN")
        val adminToken = bearerToken(adminId, "ADMIN")

        val response = patchWithAuth(
            "/api/users/${UUID.randomUUID()}/role",
            adminToken,
            UpdateRoleRequest("ADMIN"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("ACCOUNT_NOT_FOUND", response.body?.error?.code)
    }

    companion object {

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("identity_test")
            .withUsername("identity_test")
            .withPassword("identity_test")

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.baseline-on-migrate") { "false" }

            val tempKeyDir = Files.createTempDirectory("identity-service-test-keys")
            registry.add("identity.security.jwt.private-key-path") { tempKeyDir.resolve("private.pem").toString() }
            registry.add("identity.security.jwt.public-key-export-path") { tempKeyDir.resolve("public.pem").toString() }
        }
    }
}
