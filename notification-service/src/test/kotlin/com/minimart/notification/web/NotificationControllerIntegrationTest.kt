package com.minimart.notification.web

import com.minimart.notification.infrastructure.messaging.RabbitMqConsumerConfig
import com.minimart.notification.web.dto.ErrorResponse
import com.minimart.notification.web.dto.NotificationListResponse
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
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
import org.testcontainers.containers.MongoDBContainer
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
 * End-to-end slice test for Phase 7: real Spring context, real HTTP, real
 * MongoDB + RabbitMQ (Testcontainers) — publishes directly to the
 * `order.events` exchange (the same one order-service's own integration
 * test proves it publishes to) and asserts the resulting notification
 * shows up via GET /api/notifications, exercising OrderEventListener's
 * real manual-ack consumption path, not a Kotlin interface fake.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class NotificationControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var rabbitTemplate: RabbitTemplate

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

    private fun headers(token: String): HttpHeaders = HttpHeaders().apply { setBearerAuth(token) }

    /**
     * `rabbitTemplate.send` with a hand-built [Message] — not `convertAndSend`,
     * which would run this test's own JSON *string* through the configured
     * `JacksonJsonMessageConverter` a second time, serializing it as a quoted
     * JSON string literal rather than sending the raw JSON bytes order-service
     * actually puts on the wire.
     */
    private fun publishRawJson(routingKey: String, json: String) {
        val properties = MessageProperties().apply { contentType = "application/json" }
        rabbitTemplate.send(RabbitMqConsumerConfig.EXCHANGE_NAME, routingKey, Message(json.toByteArray(), properties))
    }

    private fun publishOrderEvent(routingKey: String, orderId: UUID, customerId: UUID) {
        val payload = """{"orderId":"$orderId","customerId":"$customerId","totalAmount":"89.99","placedAt":"${Instant.now()}"}"""
        publishRawJson(routingKey, payload)
    }

    private fun listNotifications(token: String): NotificationListResponse? =
        restTemplate.exchange(
            "/api/notifications", HttpMethod.GET, HttpEntity<Void>(headers(token)), NotificationListResponse::class.java,
        ).body

    /** Polls GET /api/notifications since consumption happens asynchronously on the listener container's own thread. */
    private fun awaitNotificationFor(token: String, orderId: UUID, timeoutMillis: Long = 10_000): NotificationListResponse {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val response = listNotifications(token)
            if (response != null && response.items.any { it.orderId == orderId.toString() }) {
                return response
            }
            Thread.sleep(200)
        }
        fail<Unit>("No notification for orderId=$orderId appeared within ${timeoutMillis}ms")
        error("unreachable")
    }

    // ---- event consumption (NTF-001/NTF-002, scenarios 24/25) -------------------------------

    @Test
    fun `an order-placed event is recorded and visible via GET notifications`() {
        val customerId = UUID.randomUUID()
        val orderId = UUID.randomUUID()

        publishOrderEvent(RabbitMqConsumerConfig.ORDER_PLACED_ROUTING_KEY, orderId, customerId)

        val response = awaitNotificationFor(bearerToken(customerId, "CUSTOMER"), orderId)
        val notification = response.items.single { it.orderId == orderId.toString() }
        assertEquals("ORDER_PLACED", notification.type)
        assertEquals("Your order has been placed.", notification.message)
        assertNotNull(notification.createdAt)
    }

    @Test
    fun `an order-cancelled event is recorded and visible via GET notifications`() {
        val customerId = UUID.randomUUID()
        val orderId = UUID.randomUUID()

        publishOrderEvent(RabbitMqConsumerConfig.ORDER_CANCELLED_ROUTING_KEY, orderId, customerId)

        val response = awaitNotificationFor(bearerToken(customerId, "CUSTOMER"), orderId)
        val notification = response.items.single { it.orderId == orderId.toString() }
        assertEquals("ORDER_CANCELLED", notification.type)
        assertEquals("Your order has been cancelled.", notification.message)
    }

    @Test
    fun `a malformed message is dead-lettered rather than retried forever or silently dropped — scenario 28, NTF-005`() {
        // Missing the required `customerId` field entirely — OrderEventMessage deserialization
        // fails, which is the "whatever the cause" failure the Phase-7 doc names.
        val malformedPayload = """{"orderId":"${UUID.randomUUID()}"}"""
        publishRawJson(RabbitMqConsumerConfig.ORDER_PLACED_ROUTING_KEY, malformedPayload)

        val deadLettered = rabbitTemplate.receive(RabbitMqConsumerConfig.DEAD_LETTER_QUEUE_NAME, 10_000)
        assertNotNull(deadLettered, "Expected the malformed message to be dead-lettered onto ${RabbitMqConsumerConfig.DEAD_LETTER_QUEUE_NAME}")
    }

    // ---- GET /api/notifications (NTF-003, scenarios 26/27) ----------------------------------

    @Test
    fun `GET notifications with no filter returns only the caller's own history, newest first`() {
        val customerId = UUID.randomUUID()
        val firstOrderId = UUID.randomUUID()
        val secondOrderId = UUID.randomUUID()
        publishOrderEvent(RabbitMqConsumerConfig.ORDER_PLACED_ROUTING_KEY, firstOrderId, customerId)
        awaitNotificationFor(bearerToken(customerId, "CUSTOMER"), firstOrderId)
        publishOrderEvent(RabbitMqConsumerConfig.ORDER_CANCELLED_ROUTING_KEY, secondOrderId, customerId)
        val response = awaitNotificationFor(bearerToken(customerId, "CUSTOMER"), secondOrderId)

        assertEquals(2, response.total)
        assertEquals(secondOrderId.toString(), response.items.first().orderId) // newest first
    }

    @Test
    fun `GET notifications with another account's id filter returns 403`() {
        val customerId = UUID.randomUUID()

        val response = restTemplate.exchange(
            "/api/notifications?accountId=${UUID.randomUUID()}",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(customerId, "CUSTOMER"))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.error?.code)
    }

    @Test
    fun `GET notifications as an Administrator with an accountId filter returns that account's history — scenario 27`() {
        val customerId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        publishOrderEvent(RabbitMqConsumerConfig.ORDER_PLACED_ROUTING_KEY, orderId, customerId)
        awaitNotificationFor(bearerToken(customerId, "CUSTOMER"), orderId)

        val response = restTemplate.exchange(
            "/api/notifications?accountId=$customerId",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(UUID.randomUUID(), "ADMIN"))),
            NotificationListResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.items.any { it.orderId == orderId.toString() })
    }

    @Test
    fun `GET notifications as an Administrator for an account with no events returns an empty list`() {
        val response = restTemplate.exchange(
            "/api/notifications?accountId=${UUID.randomUUID()}",
            HttpMethod.GET,
            HttpEntity<Void>(headers(bearerToken(UUID.randomUUID(), "ADMIN"))),
            NotificationListResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.total)
    }

    @Test
    fun `GET notifications with no Authorization header returns 401 UNAUTHORIZED`() {
        val response = restTemplate.getForEntity("/api/notifications", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.error?.code)
    }

    companion object {

        @Container
        @JvmStatic
        val mongoDb: MongoDBContainer = MongoDBContainer("mongo:8")

        @Container
        @JvmStatic
        val rabbitMq: RabbitMQContainer = RabbitMQContainer("rabbitmq:4-management-alpine")

        private val signingKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val signingKey: PrivateKey = signingKeyPair.private

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.mongodb.uri") { mongoDb.getReplicaSetUrl("notification_test") }

            registry.add("spring.rabbitmq.host") { rabbitMq.host }
            registry.add("spring.rabbitmq.port") { rabbitMq.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbitMq.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbitMq.adminPassword }
            registry.add("spring.rabbitmq.virtual-host") { "/" }

            val publicKeyFile = Files.createTempDirectory("notification-service-test-keys").resolve("public.pem").toFile()
            val base64 = Base64.getEncoder().encodeToString(signingKeyPair.public.encoded)
            publicKeyFile.writeText(
                buildString {
                    append("-----BEGIN PUBLIC KEY-----\n")
                    base64.chunked(64).forEach { line -> append(line).append('\n') }
                    append("-----END PUBLIC KEY-----\n")
                },
            )
            registry.add("notification.security.jwt.public-key-path") { publicKeyFile.path }
        }
    }
}
