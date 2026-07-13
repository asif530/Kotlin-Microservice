package com.minimart.identity.web

import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.exception.InvalidCredentialsException
import com.minimart.identity.web.dto.ErrorBody
import com.minimart.identity.web.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Produces the project-wide {"error":{"code":"...","message":"..."}}
 * envelope for every error this service can return. `VALIDATION_ERROR` and
 * `INTERNAL_ERROR` are not defined by the Phase-1 doc (it only fixes
 * EMAIL_ALREADY_REGISTERED and INVALID_CREDENTIALS) — both are this
 * implementation's own choice, called out here and in the task summary
 * rather than left unstated.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailAlreadyRegistered(ex: EmailAlreadyRegisteredException): ResponseEntity<ErrorResponse> {
        logger.warn("409 EMAIL_ALREADY_REGISTERED")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(ErrorBody("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.")),
        )
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        logger.warn("401 INVALID_CREDENTIALS")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(ErrorBody("INVALID_CREDENTIALS", "Email or password is incorrect.")),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationFailure(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(separator = "; ") { fieldError -> "${fieldError.field}: ${fieldError.defaultMessage}" }
            .ifBlank { "Request validation failed." }
        logger.warn("400 VALIDATION_ERROR: {}", message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", message)),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: request body missing or malformed")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", "Request body is missing or malformed JSON.")),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("500 INTERNAL_ERROR: unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(ErrorBody("INTERNAL_ERROR", "An unexpected error occurred.")),
        )
    }
}
