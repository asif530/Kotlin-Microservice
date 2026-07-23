package com.minimart.catalog.web

import com.minimart.catalog.domain.exception.ForbiddenActionException
import com.minimart.catalog.domain.exception.InvalidPriceException
import com.minimart.catalog.domain.exception.ProductHasOrderHistoryException
import com.minimart.catalog.domain.exception.ProductNotFoundException
import com.minimart.catalog.domain.exception.ProductValidationException
import com.minimart.catalog.domain.exception.UnauthenticatedException
import com.minimart.catalog.web.dto.ErrorBody
import com.minimart.catalog.web.dto.ErrorResponse
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
 * envelope for every error this service can return, mirroring
 * identity-service's own GlobalExceptionHandler. `INVALID_PRICE`,
 * `FORBIDDEN`, `PRODUCT_NOT_FOUND` (Phase-3), and `PRODUCT_HAS_ORDER_HISTORY`
 * (Phase-4) are fixed by their respective doc's own response examples;
 * `UNAUTHORIZED` and `VALIDATION_ERROR` are not shown by either doc and are
 * this implementation's own judgment call, following the same precedent
 * identity-service's Phase-2 implementation already set.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(InvalidPriceException::class)
    fun handleInvalidPrice(ex: InvalidPriceException): ResponseEntity<ErrorResponse> {
        logger.warn("400 INVALID_PRICE: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("INVALID_PRICE", ex.message ?: "unitPrice is invalid.")),
        )
    }

    @ExceptionHandler(ForbiddenActionException::class)
    fun handleForbiddenAction(ex: ForbiddenActionException): ResponseEntity<ErrorResponse> {
        logger.warn("403 FORBIDDEN: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(ErrorBody("FORBIDDEN", ex.message ?: "You are not allowed to perform this action.")),
        )
    }

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(ex: ProductNotFoundException): ResponseEntity<ErrorResponse> {
        logger.info("404 PRODUCT_NOT_FOUND: productId={}", ex.productId)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(ErrorBody("PRODUCT_NOT_FOUND", "No product with this id.")),
        )
    }

    @ExceptionHandler(ProductHasOrderHistoryException::class)
    fun handleProductHasOrderHistory(ex: ProductHasOrderHistoryException): ResponseEntity<ErrorResponse> {
        logger.warn("409 PRODUCT_HAS_ORDER_HISTORY: productId={}", ex.productId)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(ErrorBody("PRODUCT_HAS_ORDER_HISTORY", ex.message ?: "This product cannot be deleted.")),
        )
    }

    @ExceptionHandler(ProductValidationException::class)
    fun handleProductValidation(ex: ProductValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", ex.message ?: "Request validation failed.")),
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
