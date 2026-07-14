package com.minimart.identity.application

import com.minimart.identity.application.dto.PromoteToAdminCommand
import com.minimart.identity.application.dto.UpdateAccountStatusCommand
import com.minimart.identity.application.dto.UpdateOwnProfileCommand
import com.minimart.identity.application.dto.ViewAccountCommand
import com.minimart.identity.domain.model.Account
import java.util.UUID

/**
 * Inbound port (use-case boundary) for Phase-2's self-service and
 * admin-management endpoints. The web layer (UserController) depends on
 * this interface, not on the concrete UserAccountService, mirroring
 * AuthUseCase/AuthController's Phase-1 split.
 */
interface UserAccountUseCase {

    /**
     * ACC-011: any authenticated caller can view their own profile
     * (GET /api/users/me).
     */
    fun getOwnProfile(callerId: UUID): Account

    /**
     * ACC-011: any authenticated caller can update their own full name
     * (PATCH /api/users/me). Email is the account's unique identifier and
     * is out of scope for this rule (see the Phase-2 doc).
     */
    fun updateOwnProfile(command: UpdateOwnProfileCommand): Account

    /**
     * ACC-011: an Administrator can view any account for support. Any
     * other caller — including a Customer viewing their own id through
     * this route rather than /me — is rejected; self-view goes through
     * [getOwnProfile], not this use case.
     *
     * @throws com.minimart.identity.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.identity.domain.exception.AccountNotFoundException
     *   if no account with the given id exists.
     */
    fun getAccountForAdmin(command: ViewAccountCommand): Account

    /**
     * ACC-008: only an Administrator can deactivate or reactivate an
     * account — including their own. ACC-006 means status is always
     * exactly one of ACTIVE/DEACTIVATED, so the same use case handles both
     * directions.
     *
     * @throws com.minimart.identity.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.identity.domain.exception.AccountNotFoundException
     *   if no account with the given id exists.
     */
    fun updateAccountStatus(command: UpdateAccountStatusCommand): Account

    /**
     * ACC-009: only an existing Administrator can promote another account
     * to Administrator. There is no self-service path, and no demotion
     * path — BUSINESS_RULES.md never defines one.
     *
     * @throws com.minimart.identity.domain.exception.ForbiddenActionException
     *   if the caller is not an Administrator.
     * @throws com.minimart.identity.domain.exception.AccountNotFoundException
     *   if no account with the given id exists.
     */
    fun promoteToAdmin(command: PromoteToAdminCommand): Account
}
