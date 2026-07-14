package com.minimart.identity.web

import com.minimart.identity.application.UserAccountUseCase
import com.minimart.identity.application.dto.PromoteToAdminCommand
import com.minimart.identity.application.dto.UpdateAccountStatusCommand
import com.minimart.identity.application.dto.UpdateOwnProfileCommand
import com.minimart.identity.application.dto.ViewAccountCommand
import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.model.CallerPrincipal
import com.minimart.identity.web.dto.UpdateProfileRequest
import com.minimart.identity.web.dto.UpdateProfileResponse
import com.minimart.identity.web.dto.UpdateRoleRequest
import com.minimart.identity.web.dto.UpdateRoleResponse
import com.minimart.identity.web.dto.UpdateStatusRequest
import com.minimart.identity.web.dto.UpdateStatusResponse
import com.minimart.identity.web.dto.UserProfileResponse
import com.minimart.identity.web.dto.toUpdateProfileResponse
import com.minimart.identity.web.dto.toUpdateRoleResponse
import com.minimart.identity.web.dto.toUpdateStatusResponse
import com.minimart.identity.web.dto.toUserProfileResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Phase 2 — Identity self-service & admin account management. Implements
 * the five endpoints specified in
 * Archive/Development/Backend/Phase/Phase-2-Identity-Self-Service-And-Admin-Management.
 *
 * Every method here requires a [CallerPrincipal] parameter, which Spring MVC
 * only supplies via [com.minimart.identity.web.security.CallerPrincipalArgumentResolver]
 * for routes covered by [com.minimart.identity.web.security.JwtSecurityWebConfig]'s
 * filter registration (paths under /api/users) — every route in this controller.
 * Kong verifies the token's signature and expiry at the gateway
 * (kong.decl.yaml's `identity-users-*` routes) but never role-based
 * authorization; that gate is entirely this service's own responsibility,
 * enforced in UserAccountService (ACC-008/ACC-009/ACC-011), not here — this
 * controller only maps HTTP <-> the use-case boundary.
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userAccountUseCase: UserAccountUseCase,
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @GetMapping("/me")
    fun getOwnProfile(caller: CallerPrincipal): ResponseEntity<UserProfileResponse> {
        logger.info("GET /api/users/me")
        val account = userAccountUseCase.getOwnProfile(caller.accountId)
        return ResponseEntity.ok(account.toUserProfileResponse())
    }

    @PatchMapping("/me")
    fun updateOwnProfile(
        caller: CallerPrincipal,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<UpdateProfileResponse> {
        logger.info("PATCH /api/users/me")
        val account = userAccountUseCase.updateOwnProfile(
            UpdateOwnProfileCommand(callerId = caller.accountId, fullName = request.fullName),
        )
        return ResponseEntity.ok(account.toUpdateProfileResponse())
    }

    @GetMapping("/{id}")
    fun getAccountById(caller: CallerPrincipal, @PathVariable id: UUID): ResponseEntity<UserProfileResponse> {
        logger.info("GET /api/users/{}", id)
        val account = userAccountUseCase.getAccountForAdmin(
            ViewAccountCommand(callerId = caller.accountId, callerRole = caller.role, targetAccountId = id),
        )
        return ResponseEntity.ok(account.toUserProfileResponse())
    }

    @PatchMapping("/{id}/status")
    fun updateStatus(
        caller: CallerPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateStatusRequest,
    ): ResponseEntity<UpdateStatusResponse> {
        logger.info("PATCH /api/users/{}/status", id)
        val account = userAccountUseCase.updateAccountStatus(
            UpdateAccountStatusCommand(
                callerId = caller.accountId,
                callerRole = caller.role,
                targetAccountId = id,
                newStatus = AccountStatus.valueOf(request.status),
            ),
        )
        return ResponseEntity.ok(account.toUpdateStatusResponse())
    }

    @PatchMapping("/{id}/role")
    fun updateRole(
        caller: CallerPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): ResponseEntity<UpdateRoleResponse> {
        logger.info("PATCH /api/users/{}/role", id)
        val account = userAccountUseCase.promoteToAdmin(
            PromoteToAdminCommand(callerId = caller.accountId, callerRole = caller.role, targetAccountId = id),
        )
        return ResponseEntity.ok(account.toUpdateRoleResponse())
    }
}
