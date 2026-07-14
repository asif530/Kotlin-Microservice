package com.minimart.identity.application

import com.minimart.identity.application.dto.PromoteToAdminCommand
import com.minimart.identity.application.dto.UpdateAccountStatusCommand
import com.minimart.identity.application.dto.UpdateOwnProfileCommand
import com.minimart.identity.application.dto.ViewAccountCommand
import com.minimart.identity.domain.exception.AccountNotFoundException
import com.minimart.identity.domain.exception.ForbiddenActionException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.RoleCode
import com.minimart.identity.domain.port.AccountRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Use-case interactor for Phase-2's self-service and admin-management
 * endpoints. Depends only on the domain's AccountRepositoryPort (constructor
 * injection, no field injection), mirroring AuthService's Phase-1 style.
 *
 * Authorization decisions (ACC-008/ACC-009/ACC-011's admin-only gates) live
 * here, not in the web layer: who is allowed to do what is a business rule,
 * not an HTTP concern, so it belongs on this side of the Clean Architecture
 * boundary. Every admin-only method checks the caller's role *before*
 * looking up the target account, so a non-admin caller learns nothing about
 * whether a given target id exists.
 */
@Service
class UserAccountService(
    private val accountRepository: AccountRepositoryPort,
    private val meterRegistry: MeterRegistry,
) : UserAccountUseCase {

    private val logger = LoggerFactory.getLogger(UserAccountService::class.java)

    override fun getOwnProfile(callerId: UUID): Account {
        val account = accountRepository.findById(callerId) ?: run {
            // Should not be reachable in practice — a verified token's
            // account id always exists (GEN-003: no deletion process
            // exists in this system) — but the port is nullable, so this is
            // handled explicitly rather than assumed away.
            logger.error("Authenticated caller id={} has no matching account row", callerId)
            meterRegistry.counter(METRIC_VIEW_SELF, "result", "not_found").increment()
            throw AccountNotFoundException(callerId)
        }

        logger.info("Profile viewed id={}", callerId)
        meterRegistry.counter(METRIC_VIEW_SELF, "result", "success").increment()
        return account
    }

    override fun updateOwnProfile(command: UpdateOwnProfileCommand): Account {
        val existing = accountRepository.findById(command.callerId) ?: run {
            logger.error("Authenticated caller id={} has no matching account row", command.callerId)
            meterRegistry.counter(METRIC_UPDATE_SELF, "result", "not_found").increment()
            throw AccountNotFoundException(command.callerId)
        }

        val updated = accountRepository.update(
            existing.copy(fullName = command.fullName, updatedAt = Instant.now()),
        )
        logger.info("Profile updated id={}", updated.id)
        meterRegistry.counter(METRIC_UPDATE_SELF, "result", "success").increment()
        return updated
    }

    override fun getAccountForAdmin(command: ViewAccountCommand): Account {
        requireAdmin(command.callerId, command.callerRole, METRIC_VIEW_ADMIN, MESSAGE_VIEW_FORBIDDEN)

        val account = accountRepository.findById(command.targetAccountId) ?: run {
            logger.warn("Admin id={} requested nonexistent account id={}", command.callerId, command.targetAccountId)
            meterRegistry.counter(METRIC_VIEW_ADMIN, "result", "not_found").increment()
            throw AccountNotFoundException(command.targetAccountId)
        }

        logger.info("Admin id={} viewed account id={}", command.callerId, account.id)
        meterRegistry.counter(METRIC_VIEW_ADMIN, "result", "success").increment()
        return account
    }

    override fun updateAccountStatus(command: UpdateAccountStatusCommand): Account {
        requireAdmin(command.callerId, command.callerRole, METRIC_UPDATE_STATUS, MESSAGE_STATUS_FORBIDDEN)

        val existing = accountRepository.findById(command.targetAccountId) ?: run {
            logger.warn("Admin id={} attempted to change status of nonexistent account id={}", command.callerId, command.targetAccountId)
            meterRegistry.counter(METRIC_UPDATE_STATUS, "result", "not_found").increment()
            throw AccountNotFoundException(command.targetAccountId)
        }

        val updated = accountRepository.update(
            existing.copy(status = command.newStatus, updatedAt = Instant.now()),
        )
        logger.info("Admin id={} set account id={} status={}", command.callerId, updated.id, updated.status)
        meterRegistry.counter(METRIC_UPDATE_STATUS, "result", "success").increment()
        return updated
    }

    override fun promoteToAdmin(command: PromoteToAdminCommand): Account {
        requireAdmin(command.callerId, command.callerRole, METRIC_UPDATE_ROLE, MESSAGE_ROLE_FORBIDDEN)

        val existing = accountRepository.findById(command.targetAccountId) ?: run {
            logger.warn("Admin id={} attempted to promote nonexistent account id={}", command.callerId, command.targetAccountId)
            meterRegistry.counter(METRIC_UPDATE_ROLE, "result", "not_found").increment()
            throw AccountNotFoundException(command.targetAccountId)
        }

        val updated = accountRepository.update(
            existing.copy(role = RoleCode.ADMIN, updatedAt = Instant.now()),
        )
        logger.info("Admin id={} promoted account id={} to ADMIN", command.callerId, updated.id)
        meterRegistry.counter(METRIC_UPDATE_ROLE, "result", "success").increment()
        return updated
    }

    /** ACC-008/ACC-009/ACC-011's shared admin-only gate — throws before any target lookup happens. */
    private fun requireAdmin(callerId: UUID, callerRole: RoleCode, metricName: String, forbiddenMessage: String) {
        if (callerRole != RoleCode.ADMIN) {
            logger.warn("Forbidden: caller id={} role={} attempted an admin-only action", callerId, callerRole.dbCode)
            meterRegistry.counter(metricName, "result", "forbidden").increment()
            throw ForbiddenActionException(forbiddenMessage)
        }
    }

    private companion object {
        const val METRIC_VIEW_SELF = "identity.users.view_self"
        const val METRIC_UPDATE_SELF = "identity.users.update_self"
        const val METRIC_VIEW_ADMIN = "identity.users.view_admin"
        const val METRIC_UPDATE_STATUS = "identity.users.update_status"
        const val METRIC_UPDATE_ROLE = "identity.users.update_role"

        // Exact wording fixed by the Phase-2 doc's 403 response examples.
        const val MESSAGE_VIEW_FORBIDDEN = "You can only view your own account."
        const val MESSAGE_STATUS_FORBIDDEN = "Only an Administrator can change an account's status."
        const val MESSAGE_ROLE_FORBIDDEN = "Only an Administrator can grant the Administrator role."
    }
}
