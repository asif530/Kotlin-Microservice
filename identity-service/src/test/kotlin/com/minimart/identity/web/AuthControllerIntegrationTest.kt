package com.minimart.identity.web

import com.minimart.identity.infrastructure.security.RsaKeyPairProvider
import com.minimart.identity.web.dto.ErrorResponse
import com.minimart.identity.web.dto.LoginRequest
import com.minimart.identity.web.dto.LoginResponse
import com.minimart.identity.web.dto.RegisterRequest
import com.minimart.identity.web.dto.RegisterResponse
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.util.UUID

/**
 * End-to-end slice test: real Spring context, real HTTP, real Postgres (via
 * Testcontainers, image matched to docker-compose.yml's postgres:17-alpine)
 * running our actual V1/V2 Flyway migrations from scratch — this is the
 * strongest available check that the migrations, JPA mapping (including the
 * citext email column), and the two endpoints all agree with each other and
 * with the Phase-1 doc's exact request/response contract.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var rsaKeyPairProvider: RsaKeyPairProvider

    @Test
    fun `register returns 201 with the exact Phase-1 response shape`() {
        val email = uniqueEmail("alice")
        val request = RegisterRequest(email, "correct-horse-battery-staple", "Alice Nguyen")

        val response = restTemplate.postForEntity("/api/auth/register", request, RegisterResponse::class.java)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = requireNotNull(response.body)
        assertNotNull(UUID.fromString(body.id)) // is a UUID string
        assertEquals(email, body.email)
        assertEquals("Alice Nguyen", body.fullName)
        assertEquals("CUSTOMER", body.role)
        assertEquals("ACTIVE", body.status)
        assertTrue(body.createdAt.isNotBlank())
    }

    @Test
    fun `register with a case-only duplicate email returns 409 EMAIL_ALREADY_REGISTERED`() {
        val email = uniqueEmail("bob")
        restTemplate.postForEntity(
            "/api/auth/register",
            RegisterRequest(email, "pw", "Bob"),
            RegisterResponse::class.java,
        )

        val response = restTemplate.postForEntity(
            "/api/auth/register",
            RegisterRequest(email.uppercase(), "different-pw", "Bob Duplicate"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("EMAIL_ALREADY_REGISTERED", response.body?.error?.code)
        assertEquals("An account with this email already exists.", response.body?.error?.message)
    }

    @Test
    fun `register with a blank field returns 400 VALIDATION_ERROR`() {
        val response = restTemplate.postForEntity(
            "/api/auth/register",
            RegisterRequest("", "pw", "Someone"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
    }

    @Test
    fun `login with correct credentials returns a verifiable RS256 access token`() {
        val email = uniqueEmail("carol")
        restTemplate.postForEntity(
            "/api/auth/register",
            RegisterRequest(email, "s3cret-password", "Carol"),
            RegisterResponse::class.java,
        )

        val response = restTemplate.postForEntity(
            "/api/auth/login",
            LoginRequest(email, "s3cret-password"),
            LoginResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals("Bearer", body.tokenType)
        assertEquals(3600L, body.expiresIn)

        val claims = Jwts.parser()
            .verifyWith(rsaKeyPairProvider.publicKey)
            .build()
            .parseSignedClaims(body.accessToken)
            .payload
        assertEquals("CUSTOMER", claims["role"])
        assertNotNull(UUID.fromString(claims.subject))
    }

    @Test
    fun `the three ACC-005 login failure causes all return the byte-identical 401 body`() {
        val email = uniqueEmail("dave")
        restTemplate.postForEntity(
            "/api/auth/register",
            RegisterRequest(email, "right-password", "Dave"),
            RegisterResponse::class.java,
        )
        val deactivatedEmail = uniqueEmail("erin-deactivated")
        insertDeactivatedAccount(deactivatedEmail, "right-password")

        val wrongPassword = restTemplate.postForEntity(
            "/api/auth/login",
            LoginRequest(email, "wrong-password"),
            ErrorResponse::class.java,
        )
        val unknownEmail = restTemplate.postForEntity(
            "/api/auth/login",
            LoginRequest(uniqueEmail("nobody"), "irrelevant"),
            ErrorResponse::class.java,
        )
        val deactivatedAccount = restTemplate.postForEntity(
            "/api/auth/login",
            LoginRequest(deactivatedEmail, "right-password"),
            ErrorResponse::class.java,
        )

        for (response in listOf(wrongPassword, unknownEmail, deactivatedAccount)) {
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
            assertEquals("INVALID_CREDENTIALS", response.body?.error?.code)
            assertEquals("Email or password is incorrect.", response.body?.error?.message)
        }
    }

    private fun uniqueEmail(localPart: String): String = "$localPart+${UUID.randomUUID()}@example.test"

    private fun insertDeactivatedAccount(email: String, rawPassword: String) {
        jdbcTemplate.update(
            """
            INSERT INTO accounts (id, email, password_hash, full_name, role_id, status)
            VALUES (?, ?, ?, ?, (SELECT id FROM roles WHERE code = 'CUSTOMER'), 'DEACTIVATED')
            """.trimIndent(),
            UUID.randomUUID(),
            email,
            passwordEncoder.encode(rawPassword),
            "Deactivated Test Account",
        )
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
            // This container starts empty every run, so Flyway runs V1/V2 from
            // scratch here — unlike application.yml's default baseline-* setup,
            // which exists only to reconcile the one pre-provisioned dev
            // container (see application.yml's flyway comment).
            registry.add("spring.flyway.baseline-on-migrate") { "false" }

            val tempKeyDir = Files.createTempDirectory("identity-service-test-keys")
            registry.add("identity.security.jwt.private-key-path") { tempKeyDir.resolve("private.pem").toString() }
            registry.add("identity.security.jwt.public-key-export-path") { tempKeyDir.resolve("public.pem").toString() }
        }
    }
}
