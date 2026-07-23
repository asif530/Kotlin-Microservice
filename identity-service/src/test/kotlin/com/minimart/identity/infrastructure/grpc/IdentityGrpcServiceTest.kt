package com.minimart.identity.infrastructure.grpc

import com.minimart.identity.application.testsupport.FakeAccountRepository
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import com.minimart.identity.grpc.getUserRequest
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure unit test for Phase-5's gRPC server (identity.proto's GetUser) — no
 * Spring context, no real network, mirrors the rest of this service's
 * application-layer test style (e.g. UserAccountServiceTest).
 */
class IdentityGrpcServiceTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var service: IdentityGrpcService

    @BeforeEach
    fun setUp() {
        accountRepository = FakeAccountRepository()
        service = IdentityGrpcService(accountRepository)
    }

    private fun seedAccount(status: AccountStatus): Account {
        val now = Instant.now()
        return accountRepository.save(
            Account(
                id = UUID.randomUUID(),
                email = "alice@example.test",
                passwordHash = "irrelevant",
                fullName = "Alice Nguyen",
                role = RoleCode.CUSTOMER,
                status = status,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Test
    fun `getUser returns active=true for an ACTIVE account`() = runBlocking {
        val account = seedAccount(AccountStatus.ACTIVE)

        val response = service.getUser(getUserRequest { userId = account.id.toString() })

        assertEquals(account.id.toString(), response.userId)
        assertEquals(account.email, response.email)
        assertEquals(account.fullName, response.fullName)
        assertTrue(response.active)
    }

    @Test
    fun `getUser returns active=false for a Deactivated account — ORD-001's rejection signal`() = runBlocking {
        val account = seedAccount(AccountStatus.DEACTIVATED)

        val response = service.getUser(getUserRequest { userId = account.id.toString() })

        assertEquals(false, response.active)
    }

    @Test
    fun `getUser throws NOT_FOUND for a nonexistent account id`() {
        val exception = assertThrows(StatusException::class.java) {
            runBlocking { service.getUser(getUserRequest { userId = UUID.randomUUID().toString() }) }
        }
        assertEquals(Status.Code.NOT_FOUND, exception.status.code)
    }

    @Test
    fun `getUser throws INVALID_ARGUMENT for a malformed user id`() {
        val exception = assertThrows(StatusException::class.java) {
            runBlocking { service.getUser(getUserRequest { userId = "not-a-uuid" }) }
        }
        assertEquals(Status.Code.INVALID_ARGUMENT, exception.status.code)
    }
}
