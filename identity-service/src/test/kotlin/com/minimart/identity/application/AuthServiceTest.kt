package com.minimart.identity.application

import com.minimart.identity.application.dto.LoginCommand
import com.minimart.identity.application.dto.RegisterCommand
import com.minimart.identity.application.testsupport.FakeAccountRepository
import com.minimart.identity.application.testsupport.FakePasswordHasher
import com.minimart.identity.application.testsupport.FakeTokenIssuer
import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.exception.InvalidCredentialsException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Pure unit tests for the use-case interactor — no Spring context, no database. */
class AuthServiceTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var passwordHasher: FakePasswordHasher
    private lateinit var tokenIssuer: FakeTokenIssuer
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        accountRepository = FakeAccountRepository()
        passwordHasher = FakePasswordHasher()
        tokenIssuer = FakeTokenIssuer()
        authService = AuthService(accountRepository, passwordHasher, tokenIssuer, SimpleMeterRegistry())
    }

    // ---- register (ACC-001, ACC-004) ----------------------------------------------------

    @Test
    fun `register assigns Customer role and Active status with no verification step`() {
        val account = authService.register(
            RegisterCommand(email = "alice.nguyen@example.test", password = "correct-horse-battery-staple", fullName = "Alice Nguyen"),
        )

        assertEquals("alice.nguyen@example.test", account.email)
        assertEquals("Alice Nguyen", account.fullName)
        assertEquals(RoleCode.CUSTOMER, account.role)
        assertEquals(AccountStatus.ACTIVE, account.status)
        assertNotNull(account.id)
        // Password is never stored/returned in the clear.
        assertTrue(account.passwordHash != "correct-horse-battery-staple")
    }

    @Test
    fun `register rejects an email that already exists (fast pre-check path)`() {
        authService.register(RegisterCommand("bob@example.test", "pw", "Bob"))

        val exception = assertThrows(EmailAlreadyRegisteredException::class.java) {
            authService.register(RegisterCommand("bob@example.test", "different-pw", "Bob Again"))
        }
        assertEquals("An account with this email already exists.", exception.message)
    }

    @Test
    fun `register rejects a case-only duplicate email, matching ACC-002 case-insensitive uniqueness`() {
        authService.register(RegisterCommand("Carol@Example.Test", "pw", "Carol"))

        assertThrows(EmailAlreadyRegisteredException::class.java) {
            authService.register(RegisterCommand("CAROL@EXAMPLE.TEST", "pw2", "Carol Duplicate"))
        }
    }

    @Test
    fun `register still surfaces EmailAlreadyRegistered when the DB constraint catches a race the pre-check missed`() {
        accountRepository.forceRaceOnNextSave = true

        assertThrows(EmailAlreadyRegisteredException::class.java) {
            authService.register(RegisterCommand("dana@example.test", "pw", "Dana"))
        }
    }

    // ---- login (ACC-005, ACC-007) --------------------------------------------------------

    @Test
    fun `login with correct password and Active account returns a token`() {
        authService.register(RegisterCommand("erin@example.test", "s3cret", "Erin"))

        val token = authService.login(LoginCommand("erin@example.test", "s3cret"))

        assertEquals("Bearer", token.tokenType)
        assertNotNull(token.token)
        assertEquals("erin@example.test", tokenIssuer.lastIssuedFor?.email)
    }

    @Test
    fun `login is case-insensitive on email like registration is`() {
        authService.register(RegisterCommand("frank@example.test", "s3cret", "Frank"))

        val token = authService.login(LoginCommand("FRANK@EXAMPLE.TEST", "s3cret"))

        assertNotNull(token.token)
    }

    @Test
    fun `login with wrong password throws InvalidCredentials`() {
        authService.register(RegisterCommand("gina@example.test", "right-password", "Gina"))

        assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("gina@example.test", "wrong-password"))
        }
    }

    @Test
    fun `login with an unregistered email throws InvalidCredentials`() {
        assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("nobody@example.test", "whatever"))
        }
    }

    @Test
    fun `login against a Deactivated account throws InvalidCredentials even with the correct password`() {
        val now = Instant.now()
        val deactivated = Account(
            id = UUID.randomUUID(),
            email = "henry@example.test",
            passwordHash = passwordHasher.hash("correct-password"),
            fullName = "Henry",
            role = RoleCode.CUSTOMER,
            status = AccountStatus.DEACTIVATED,
            createdAt = now,
            updatedAt = now,
        )
        accountRepository.save(deactivated)

        assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("henry@example.test", "correct-password"))
        }
    }

    @Test
    fun `all three ACC-005 login failure causes are reported with the identical exception message`() {
        // Cause 1: wrong password.
        authService.register(RegisterCommand("ivy@example.test", "right-password", "Ivy"))
        val wrongPassword = assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("ivy@example.test", "wrong-password"))
        }

        // Cause 2: unregistered email.
        val noSuchEmail = assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("nobody-at-all@example.test", "irrelevant"))
        }

        // Cause 3: Deactivated account, correct password.
        val now = Instant.now()
        accountRepository.save(
            Account(
                id = UUID.randomUUID(),
                email = "jack@example.test",
                passwordHash = passwordHasher.hash("right-password"),
                fullName = "Jack",
                role = RoleCode.CUSTOMER,
                status = AccountStatus.DEACTIVATED,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val deactivatedAccount = assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("jack@example.test", "right-password"))
        }

        assertEquals(wrongPassword.message, noSuchEmail.message)
        assertEquals(wrongPassword.message, deactivatedAccount.message)
        assertEquals("Email or password is incorrect.", wrongPassword.message)
    }

    @Test
    fun `login never leaks whether the account was found via any exposed exception state`() {
        assertNull(tokenIssuer.lastIssuedFor)
        assertThrows(InvalidCredentialsException::class.java) {
            authService.login(LoginCommand("truly-unknown@example.test", "whatever"))
        }
        // No token was ever handed to the issuer for a failed attempt.
        assertNull(tokenIssuer.lastIssuedFor)
    }
}
