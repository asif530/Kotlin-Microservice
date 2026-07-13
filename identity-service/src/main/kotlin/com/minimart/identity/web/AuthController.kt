package com.minimart.identity.web

import com.minimart.identity.application.AuthUseCase
import com.minimart.identity.application.dto.LoginCommand
import com.minimart.identity.application.dto.RegisterCommand
import com.minimart.identity.web.dto.LoginRequest
import com.minimart.identity.web.dto.LoginResponse
import com.minimart.identity.web.dto.RegisterRequest
import com.minimart.identity.web.dto.RegisterResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Phase 1 — Identity foundations. Implements exactly the two endpoints
 * specified in Archive/Development/Backend/Phase/Phase-1-Identity-Foundations:
 * POST /api/auth/register and POST /api/auth/login. Kong excludes both from
 * its JWT plugin (ARCHITECTURE.md §7) — you can't present a token before you
 * have one — so no authentication is enforced here.
 *
 * Depends on the AuthUseCase port, not the concrete AuthService, and maps
 * domain objects to response DTOs itself so JPA entities never leak out as
 * API responses.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
) {

    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<RegisterResponse> {
        logger.info("POST /api/auth/register")
        val account = authUseCase.register(
            RegisterCommand(email = request.email, password = request.password, fullName = request.fullName),
        )
        val response = RegisterResponse(
            id = account.id.toString(),
            email = account.email,
            fullName = account.fullName,
            role = account.role.dbCode,
            status = account.status.name,
            createdAt = account.createdAt.toString(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        logger.info("POST /api/auth/login")
        val issuedToken = authUseCase.login(LoginCommand(email = request.email, password = request.password))
        val response = LoginResponse(
            accessToken = issuedToken.token,
            tokenType = issuedToken.tokenType,
            expiresIn = issuedToken.expiresInSeconds,
        )
        return ResponseEntity.ok(response)
    }
}
