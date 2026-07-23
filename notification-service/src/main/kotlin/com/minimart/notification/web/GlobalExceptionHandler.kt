package com.minimart.notification.web

import com.minimart.notification.domain.exception.ForbiddenActionException
import com.minimart.notification.domain.exception.UnauthenticatedException
import com.minimart.notification.web.dto.ErrorBody
import com.minimart.notification.web.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Produces the project-wide {"error":{"code":"...","message":"..."}}
 * envelope for every error this service can return, mirroring
 * identity-service/catalog-service/order-service's own
 * GlobalExceptionHandler. `FORBIDDEN`, `UNAUTHORIZED`, and
 * `VALIDATION_ERROR` are not shown by the Phase-7 doc (NTF-003's 403 case
 * is mentioned but no exact body is given) and are this implementation's
 * own judgment call, following the same precedent the other three
 * services already set.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ForbiddenActionException::class)
    fun handleForbiddenAction(ex: ForbiddenActionException): ResponseEntity<ErrorResponse> {
        logger.warn("403 FORBIDDEN: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(ErrorBody("FORBIDDEN", ex.message ?: "You are not allowed to perform this action.")),
        )
    }

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ResponseEntity<ErrorResponse> {
        logger.warn("401 UNAUTHORIZED: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(ErrorBody("UNAUTHORIZED", ex.message ?: "Authentication is required.")),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: query parameter '{}' is malformed", ex.name)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", "'${ex.name}' is not a validly formatted value.")),
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
