package com.minimart.order.web

import com.minimart.order.domain.exception.ForbiddenActionException
import com.minimart.order.domain.exception.InsufficientStockException
import com.minimart.order.domain.exception.NotEligibleToOrderException
import com.minimart.order.domain.exception.OrderNotCancellableException
import com.minimart.order.domain.exception.OrderNotFoundException
import com.minimart.order.domain.exception.OrderValidationException
import com.minimart.order.domain.exception.UnauthenticatedException
import com.minimart.order.web.dto.ErrorBody
import com.minimart.order.web.dto.ErrorResponse
import com.minimart.order.web.dto.StockFailureDetailResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Produces the project-wide {"error":{"code":"...","message":"..."}}
 * envelope for every error this service can return, mirroring
 * identity-service/catalog-service's own GlobalExceptionHandler.
 * `NOT_ELIGIBLE_TO_ORDER`/`INSUFFICIENT_STOCK`/`ORDER_NOT_FOUND` (Phase 5)
 * and `ORDER_NOT_CANCELLABLE` (Phase 6) are fixed by their respective
 * doc's own response examples; `FORBIDDEN`, `UNAUTHORIZED`, and
 * `VALIDATION_ERROR` are not shown by either doc and are this
 * implementation's own judgment call, following the same precedent
 * identity-service/catalog-service already set.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotEligibleToOrderException::class)
    fun handleNotEligibleToOrder(ex: NotEligibleToOrderException): ResponseEntity<ErrorResponse> {
        logger.warn("403 NOT_ELIGIBLE_TO_ORDER: {}", ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(ErrorBody("NOT_ELIGIBLE_TO_ORDER", ex.message ?: "This account cannot place an order.")),
        )
    }

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(ex: InsufficientStockException): ResponseEntity<ErrorResponse> {
        logger.info("409 INSUFFICIENT_STOCK: {}", ex.details)
        val details = ex.details.map { StockFailureDetailResponse(it.productId, it.requested, it.available) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(ErrorBody("INSUFFICIENT_STOCK", ex.message ?: "Insufficient stock.", details)),
        )
    }

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<ErrorResponse> {
        logger.info("404 ORDER_NOT_FOUND: orderId={}", ex.orderId)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(ErrorBody("ORDER_NOT_FOUND", "No order with this id.")),
        )
    }

    @ExceptionHandler(OrderNotCancellableException::class)
    fun handleOrderNotCancellable(ex: OrderNotCancellableException): ResponseEntity<ErrorResponse> {
        logger.info("409 ORDER_NOT_CANCELLABLE: orderId={} currentStatus={}", ex.orderId, ex.currentStatus)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(ErrorBody("ORDER_NOT_CANCELLABLE", ex.message ?: "This order cannot be cancelled.")),
        )
    }

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

    @ExceptionHandler(OrderValidationException::class)
    fun handleOrderValidation(ex: OrderValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", ex.message ?: "Request validation failed.")),
        )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
        logger.warn("400 VALIDATION_ERROR: missing required header '{}'", ex.headerName)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(ErrorBody("VALIDATION_ERROR", "Missing required header '${ex.headerName}'.")),
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
