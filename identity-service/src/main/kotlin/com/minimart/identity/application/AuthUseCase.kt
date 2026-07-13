package com.minimart.identity.application

import com.minimart.identity.application.dto.LoginCommand
import com.minimart.identity.application.dto.RegisterCommand
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.port.IssuedToken

/**
 * Inbound port (use-case boundary) for the two Phase-1 endpoints. The web
 * layer (AuthController) depends on this interface, not on the concrete
 * AuthService, so the controller stays testable/replaceable independently
 * of how registration/login are actually implemented.
 */
interface AuthUseCase {

    /**
     * Registers a new Customer account (ACC-001, ACC-004).
     *
     * @throws com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
     *   ACC-003: the email is already registered (case-insensitive, ACC-002).
     */
    fun register(command: RegisterCommand): Account

    /**
     * Authenticates an account and issues a signed access token.
     *
     * @throws com.minimart.identity.domain.exception.InvalidCredentialsException
     *   ACC-005: wrong password, unregistered email, or a Deactivated
     *   account (ACC-007) — always this one exception, never distinguished.
     */
    fun login(command: LoginCommand): IssuedToken
}
