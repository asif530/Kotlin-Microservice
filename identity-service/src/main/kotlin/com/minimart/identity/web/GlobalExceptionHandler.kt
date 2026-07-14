package com.minimart.identity.web

import com.minimart.identity.domain.exception.AccountNotFoundException
import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.exception.ForbiddenActionException
import com.minimart.identity.domain.exception.InvalidCredentialsException
import com.minimart.identity.domain.exception.UnauthenticatedException
import com.minimart.identity.web.dto.ErrorBody
import com.minimart.identity.web.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Produces the project-wide {"error":{"code":"...","message":"..."}}
 * envelope for every error this service can return. `VALIDATION_ERROR` and
 * `INTERNAL_ERROR` are not defined by the Phase-1 doc (it only fixes
 * EMAIL_ALREADY_REGISTERED and INVALID_CREDENTIALS) — both are this
 * implementation's own choice, called out here and in the task summary
 * rather than left unstated. Phase-2 adds three more codes the same way:
 * `FORBIDDEN` is fixed by the Phase-2 doc's own response examples (message
 * varies per call site — see ForbiddenActionException); `UNAUTHORIZED` and
 * `ACCOUNT_NOT_FOUND` are not shown by the doc and are this implementation's
 * own judgment call (see UnauthenticatedException / AccountNotFoundException
 * kdoc for the reasoning).
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

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ResponseEntity<ErrorResponse> {
        logger.warn("401 UNAUTHORIZED: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(ErrorBody("UNAUTHORIZED", ex.message ?: "Authentication is required.")),
        )
    }

    @ExceptionHandler(ForbiddenActionException::class)
    fun handleForbiddenAction(ex: ForbiddenActionException): ResponseEntity<ErrorResponse> {
        logger.warn("403 FORBIDDEN: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(ErrorBody("FORBIDDEN", ex.message ?: "You are not allowed to perform this action.")),
        )
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("404 ACCOUNT_NOT_FOUND: accountId={}", ex.accountId)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(ErrorBody("ACCOUNT_NOT_FOUND", "No account exists with the given id.")),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: path variable '{}' is malformed", ex.name)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", "'${ex.name}' is not a validly formatted value.")),
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
