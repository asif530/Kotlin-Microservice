package com.minimart.identity.application

import com.minimart.identity.application.dto.LoginCommand
import com.minimart.identity.application.dto.RegisterCommand
import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.exception.InvalidCredentialsException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.RoleCode
import com.minimart.identity.domain.port.AccountRepositoryPort
import com.minimart.identity.domain.port.IssuedToken
import com.minimart.identity.domain.port.PasswordHasher
import com.minimart.identity.domain.port.TokenIssuer
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Use-case interactor for the two Phase-1 endpoints. Depends only on domain
 * ports (constructor injection, no field injection) — it has no idea whether
 * accounts are stored in Postgres, whether passwords are hashed with BCrypt,
 * or how a token is signed. That is the whole point of Clean Architecture's
 * dependency-inversion boundary here.
 */
@Service
class AuthService(
    private val accountRepository: AccountRepositoryPort,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
    private val meterRegistry: MeterRegistry,
) : AuthUseCase {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * ACC-005 nice-to-have: a fixed, pre-hashed dummy password so that
     * [login] always performs one BCrypt comparison, whether or not the
     * email is registered. This narrows (does not fully close — BCrypt cost
     * is already dominant and near-constant, but a real account also does a
     * DB row fetch) the timing gap between "email not found" and "email
     * found, wrong password". Computed once, since BCrypt hashing is
     * deliberately expensive and this exact value never needs to change.
     */
    private val dummyPasswordHash: String by lazy { passwordHasher.hash(DUMMY_PASSWORD_FOR_TIMING_PARITY) }

    override fun register(command: RegisterCommand): Account {
        logger.info("Registration attempt received for email='{}'", command.email)

        // Fast, friendly pre-check (UX only) — see AccountRepositoryPort.save
        // kdoc for why the DB constraint, not this check, is the real guard.
        if (accountRepository.existsByEmail(command.email)) {
            logger.warn("Registration rejected: email already registered (pre-check)")
            meterRegistry.counter(METRIC_REGISTER, "result", "duplicate").increment()
            throw EmailAlreadyRegisteredException(command.email)
        }

        val now = Instant.now()
        val account = Account(
            id = UUID.randomUUID(),
            email = command.email,
            passwordHash = passwordHasher.hash(command.password),
            fullName = command.fullName,
            role = RoleCode.CUSTOMER, // ACC-004: new accounts are always Customer
            status = AccountStatus.ACTIVE, // ACC-004: immediately Active, no verification step
            createdAt = now,
            updatedAt = now,
        )

        val saved = try {
            accountRepository.save(account)
        } catch (raceLoss: EmailAlreadyRegisteredException) {
            logger.warn("Registration rejected: email already registered (DB constraint caught a race)")
            meterRegistry.counter(METRIC_REGISTER, "result", "duplicate").increment()
            throw raceLoss
        }

        logger.info("Account registered id={} role={}", saved.id, saved.role.dbCode)
        meterRegistry.counter(METRIC_REGISTER, "result", "success").increment()
        return saved
    }

    override fun login(command: LoginCommand): IssuedToken {
        val account = accountRepository.findByEmail(command.email)
        val passwordMatches = passwordHasher.matches(command.password, account?.passwordHash ?: dummyPasswordHash)
        val isActive = account?.status == AccountStatus.ACTIVE
        val isValid = account != null && passwordMatches && isActive

        if (!isValid) {
            // Internal-only diagnostic detail (never reflected in the HTTP
            // response — see InvalidCredentialsException kdoc for ACC-005).
            logger.warn(
                "Login rejected: accountFound={} passwordMatched={} active={}",
                account != null,
                passwordMatches,
                isActive,
            )
            meterRegistry.counter(METRIC_LOGIN, "result", "failure").increment()
            throw InvalidCredentialsException()
        }

        val issuedToken = tokenIssuer.issue(account)
        logger.info("Login succeeded id={}", account.id)
        meterRegistry.counter(METRIC_LOGIN, "result", "success").increment()
        return issuedToken
    }

    private companion object {
        const val DUMMY_PASSWORD_FOR_TIMING_PARITY = "identity-service-dummy-password-for-timing-parity"
        const val METRIC_REGISTER = "identity.auth.register"
        const val METRIC_LOGIN = "identity.auth.login"
    }
}
