package com.minimart.identity.application

import com.minimart.identity.application.dto.PromoteToAdminCommand
import com.minimart.identity.application.dto.UpdateAccountStatusCommand
import com.minimart.identity.application.dto.UpdateOwnProfileCommand
import com.minimart.identity.application.dto.ViewAccountCommand
import com.minimart.identity.application.testsupport.FakeAccountRepository
import com.minimart.identity.domain.exception.AccountNotFoundException
import com.minimart.identity.domain.exception.ForbiddenActionException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Pure unit tests for the Phase-2 use-case interactor — no Spring context, no database. */
class UserAccountServiceTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var userAccountService: UserAccountService

    private lateinit var customer: Account
    private lateinit var admin: Account
    private lateinit var otherCustomer: Account

    @BeforeEach
    fun setUp() {
        accountRepository = FakeAccountRepository()
        userAccountService = UserAccountService(accountRepository, SimpleMeterRegistry())

        customer = seedAccount(email = "alice@example.test", fullName = "Alice Nguyen", role = RoleCode.CUSTOMER)
        admin = seedAccount(email = "admin@example.test", fullName = "Root Admin", role = RoleCode.ADMIN)
        otherCustomer = seedAccount(email = "chen@example.test", fullName = "Chen Park", role = RoleCode.CUSTOMER)
    }

    private fun seedAccount(email: String, fullName: String, role: RoleCode, status: AccountStatus = AccountStatus.ACTIVE): Account {
        val now = Instant.now()
        val account = Account(
            id = UUID.randomUUID(),
            email = email,
            passwordHash = "irrelevant-for-this-test",
            fullName = fullName,
            role = role,
            status = status,
            createdAt = now,
            updatedAt = now,
        )
        return accountRepository.save(account)
    }

    // ---- getOwnProfile (ACC-011, scenario 4) --------------------------------------------------

    @Test
    fun `getOwnProfile returns the caller's own account`() {
        val result = userAccountService.getOwnProfile(customer.id)

        assertEquals(customer.id, result.id)
        assertEquals(customer.email, result.email)
        assertEquals(customer.fullName, result.fullName)
        assertEquals(RoleCode.CUSTOMER, result.role)
        assertEquals(AccountStatus.ACTIVE, result.status)
    }

    @Test
    fun `getOwnProfile throws AccountNotFound when the token's account id has no matching row`() {
        assertThrows(AccountNotFoundException::class.java) {
            userAccountService.getOwnProfile(UUID.randomUUID())
        }
    }

    // ---- updateOwnProfile (ACC-011, scenario 5) -----------------------------------------------

    @Test
    fun `updateOwnProfile changes only fullName and bumps updatedAt`() {
        val result = userAccountService.updateOwnProfile(
            UpdateOwnProfileCommand(callerId = customer.id, fullName = "Alice N. Nguyen"),
        )

        assertEquals("Alice N. Nguyen", result.fullName)
        assertEquals(customer.email, result.email) // unchanged — email is not part of this rule
        assertEquals(customer.role, result.role) // unchanged
        assertEquals(customer.status, result.status) // unchanged
        assertTrue(result.updatedAt.isAfter(customer.updatedAt) || result.updatedAt == customer.updatedAt)
    }

    @Test
    fun `updateOwnProfile throws AccountNotFound when the token's account id has no matching row`() {
        assertThrows(AccountNotFoundException::class.java) {
            userAccountService.updateOwnProfile(UpdateOwnProfileCommand(callerId = UUID.randomUUID(), fullName = "Nobody"))
        }
    }

    // ---- getAccountForAdmin (ACC-011, scenario 6) ---------------------------------------------

    @Test
    fun `getAccountForAdmin lets an Administrator view any account`() {
        val result = userAccountService.getAccountForAdmin(
            ViewAccountCommand(callerId = admin.id, callerRole = RoleCode.ADMIN, targetAccountId = otherCustomer.id),
        )

        assertEquals(otherCustomer.id, result.id)
        assertEquals(otherCustomer.email, result.email)
    }

    @Test
    fun `getAccountForAdmin rejects a Customer viewing another account with the exact Phase-2 403 message`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.getAccountForAdmin(
                ViewAccountCommand(callerId = customer.id, callerRole = RoleCode.CUSTOMER, targetAccountId = otherCustomer.id),
            )
        }
        assertEquals("You can only view your own account.", exception.message)
    }

    @Test
    fun `getAccountForAdmin rejects a Customer viewing even their own id through this route — self-view goes through me`() {
        assertThrows(ForbiddenActionException::class.java) {
            userAccountService.getAccountForAdmin(
                ViewAccountCommand(callerId = customer.id, callerRole = RoleCode.CUSTOMER, targetAccountId = customer.id),
            )
        }
    }

    @Test
    fun `getAccountForAdmin throws AccountNotFound for a nonexistent target id`() {
        assertThrows(AccountNotFoundException::class.java) {
            userAccountService.getAccountForAdmin(
                ViewAccountCommand(callerId = admin.id, callerRole = RoleCode.ADMIN, targetAccountId = UUID.randomUUID()),
            )
        }
    }

    @Test
    fun `getAccountForAdmin checks role before existence — a non-admin gets Forbidden even for a nonexistent id`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.getAccountForAdmin(
                ViewAccountCommand(callerId = customer.id, callerRole = RoleCode.CUSTOMER, targetAccountId = UUID.randomUUID()),
            )
        }
        assertEquals("You can only view your own account.", exception.message)
    }

    // ---- updateAccountStatus (ACC-008, scenarios 7/8) -----------------------------------------

    @Test
    fun `updateAccountStatus lets an Administrator deactivate another account`() {
        val result = userAccountService.updateAccountStatus(
            UpdateAccountStatusCommand(
                callerId = admin.id,
                callerRole = RoleCode.ADMIN,
                targetAccountId = otherCustomer.id,
                newStatus = AccountStatus.DEACTIVATED,
            ),
        )

        assertEquals(AccountStatus.DEACTIVATED, result.status)
    }

    @Test
    fun `updateAccountStatus lets an Administrator reactivate a Deactivated account, role untouched`() {
        val deactivated = seedAccount("dana@example.test", "Dana", RoleCode.CUSTOMER, AccountStatus.DEACTIVATED)

        val result = userAccountService.updateAccountStatus(
            UpdateAccountStatusCommand(
                callerId = admin.id,
                callerRole = RoleCode.ADMIN,
                targetAccountId = deactivated.id,
                newStatus = AccountStatus.ACTIVE,
            ),
        )

        assertEquals(AccountStatus.ACTIVE, result.status)
        assertEquals(RoleCode.CUSTOMER, result.role) // ACC-008's reactivation doesn't touch role
    }

    @Test
    fun `updateAccountStatus rejects a Customer acting on any account — including their own — with the exact Phase-2 403 message`() {
        val onOther = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.updateAccountStatus(
                UpdateAccountStatusCommand(customer.id, RoleCode.CUSTOMER, otherCustomer.id, AccountStatus.DEACTIVATED),
            )
        }
        val onSelf = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.updateAccountStatus(
                UpdateAccountStatusCommand(customer.id, RoleCode.CUSTOMER, customer.id, AccountStatus.DEACTIVATED),
            )
        }

        assertEquals("Only an Administrator can change an account's status.", onOther.message)
        assertEquals("Only an Administrator can change an account's status.", onSelf.message)
    }

    @Test
    fun `updateAccountStatus lets an Administrator deactivate their own account — the doc states no self-exception`() {
        val result = userAccountService.updateAccountStatus(
            UpdateAccountStatusCommand(admin.id, RoleCode.ADMIN, admin.id, AccountStatus.DEACTIVATED),
        )
        assertEquals(AccountStatus.DEACTIVATED, result.status)
    }

    @Test
    fun `updateAccountStatus throws AccountNotFound for a nonexistent target id`() {
        assertThrows(AccountNotFoundException::class.java) {
            userAccountService.updateAccountStatus(
                UpdateAccountStatusCommand(admin.id, RoleCode.ADMIN, UUID.randomUUID(), AccountStatus.DEACTIVATED),
            )
        }
    }

    // ---- promoteToAdmin (ACC-009, scenario 9) -------------------------------------------------

    @Test
    fun `promoteToAdmin lets an existing Administrator promote a Customer`() {
        val result = userAccountService.promoteToAdmin(
            PromoteToAdminCommand(callerId = admin.id, callerRole = RoleCode.ADMIN, targetAccountId = otherCustomer.id),
        )
        assertEquals(RoleCode.ADMIN, result.role)
    }

    @Test
    fun `promoteToAdmin rejects a Customer acting on another account with the exact Phase-2 403 message`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.promoteToAdmin(
                PromoteToAdminCommand(customer.id, RoleCode.CUSTOMER, otherCustomer.id),
            )
        }
        assertEquals("Only an Administrator can grant the Administrator role.", exception.message)
    }

    @Test
    fun `promoteToAdmin rejects a Customer attempting self-promotion — no self-service path per ACC-009`() {
        val exception = assertThrows(ForbiddenActionException::class.java) {
            userAccountService.promoteToAdmin(
                PromoteToAdminCommand(customer.id, RoleCode.CUSTOMER, customer.id),
            )
        }
        assertEquals("Only an Administrator can grant the Administrator role.", exception.message)
    }

    @Test
    fun `promoteToAdmin throws AccountNotFound for a nonexistent target id`() {
        assertThrows(AccountNotFoundException::class.java) {
            userAccountService.promoteToAdmin(
                PromoteToAdminCommand(admin.id, RoleCode.ADMIN, UUID.randomUUID()),
            )
        }
    }
}
