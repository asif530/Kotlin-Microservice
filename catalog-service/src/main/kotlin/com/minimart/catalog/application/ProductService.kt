package com.minimart.catalog.application

import com.minimart.catalog.application.dto.CreateProductCommand
import com.minimart.catalog.application.dto.DeleteProductCommand
import com.minimart.catalog.application.dto.ListProductsCommand
import com.minimart.catalog.application.dto.UpdateProductCommand
import com.minimart.catalog.application.dto.UpdateProductStatusCommand
import com.minimart.catalog.domain.exception.ForbiddenActionException
import com.minimart.catalog.domain.exception.InvalidPriceException
import com.minimart.catalog.domain.exception.ProductHasOrderHistoryException
import com.minimart.catalog.domain.exception.ProductNotFoundException
import com.minimart.catalog.domain.exception.ProductValidationException
import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.domain.model.RoleCode
import com.minimart.catalog.domain.port.OrderHistoryPort
import com.minimart.catalog.domain.port.ProductRepositoryPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Use-case interactor for Phase-3/Phase-4's catalog endpoints. Depends only
 * on the domain's outbound ports (constructor injection, no field
 * injection), mirroring identity-service's UserAccountService style.
 *
 * CAT-006's admin-only gate lives here, not in the web layer: who is
 * allowed to mutate the catalog is a business rule, not an HTTP concern.
 * Every admin-only method checks the caller's role *before* looking up the
 * target product, mirroring UserAccountService's ACC-008/009 precedent — a
 * non-admin caller learns nothing about whether a given product id exists.
 */
@Service
class ProductService(
    private val productRepository: ProductRepositoryPort,
    private val orderHistoryPort: OrderHistoryPort,
    private val meterRegistry: MeterRegistry,
) : ProductUseCase {

    private val logger = LoggerFactory.getLogger(ProductService::class.java)

    override fun createProduct(command: CreateProductCommand): Product {
        requireAdmin(command.callerId, command.callerRole, METRIC_CREATE, MESSAGE_CREATE_FORBIDDEN)

        val unitPrice = parseUnitPrice(command.unitPriceRaw)

        val now = Instant.now()
        val product = Product(
            id = UUID.randomUUID(),
            name = command.name,
            description = command.description,
            category = command.category,
            unitPrice = unitPrice,
            stockCount = command.stockCount,
            status = ProductStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )

        val saved = productRepository.insert(product)
        logger.info("Admin id={} created product id={}", command.callerId, saved.id)
        meterRegistry.counter(METRIC_CREATE, "result", "success").increment()
        return saved
    }

    override fun listVisibleProducts(command: ListProductsCommand): List<Product> {
        val products = productRepository.searchVisible(command.category)
        meterRegistry.counter(METRIC_LIST).increment()
        return products
    }

    override fun getVisibleProduct(id: UUID): Product {
        val product = productRepository.findVisibleById(id) ?: run {
            logger.info("Product id={} not found or not visible", id)
            meterRegistry.counter(METRIC_VIEW, "result", "not_found").increment()
            throw ProductNotFoundException(id)
        }
        meterRegistry.counter(METRIC_VIEW, "result", "success").increment()
        return product
    }

    override fun updateProduct(command: UpdateProductCommand): Product {
        requireAdmin(command.callerId, command.callerRole, METRIC_UPDATE, MESSAGE_UPDATE_FORBIDDEN)

        val existing = findByIdOrThrow(command.targetProductId, METRIC_UPDATE)

        val updated = existing.copy(
            name = requireNonBlankIfPresent(command.name, "name") ?: existing.name,
            description = requireNonBlankIfPresent(command.description, "description") ?: existing.description,
            category = requireNonBlankIfPresent(command.category, "category") ?: existing.category,
            unitPrice = command.unitPriceRaw?.let { parseUnitPrice(it) } ?: existing.unitPrice,
            updatedAt = Instant.now(),
        )

        val saved = productRepository.update(updated)
        logger.info("Admin id={} updated product id={}", command.callerId, saved.id)
        meterRegistry.counter(METRIC_UPDATE, "result", "success").increment()
        return saved
    }

    override fun updateProductStatus(command: UpdateProductStatusCommand): Product {
        requireAdmin(command.callerId, command.callerRole, METRIC_UPDATE_STATUS, MESSAGE_STATUS_FORBIDDEN)

        val existing = findByIdOrThrow(command.targetProductId, METRIC_UPDATE_STATUS)

        val saved = productRepository.update(existing.copy(status = command.newStatus, updatedAt = Instant.now()))
        logger.info("Admin id={} set product id={} status={}", command.callerId, saved.id, saved.status)
        meterRegistry.counter(METRIC_UPDATE_STATUS, "result", "success").increment()
        return saved
    }

    override fun deleteProduct(command: DeleteProductCommand) {
        requireAdmin(command.callerId, command.callerRole, METRIC_DELETE, MESSAGE_DELETE_FORBIDDEN)

        val existing = findByIdOrThrow(command.targetProductId, METRIC_DELETE)

        if (orderHistoryPort.hasOrderHistory(existing.id)) {
            logger.warn("Admin id={} attempted to delete product id={} which has order history", command.callerId, existing.id)
            meterRegistry.counter(METRIC_DELETE, "result", "has_order_history").increment()
            throw ProductHasOrderHistoryException(existing.id)
        }

        productRepository.deleteById(existing.id)
        logger.info("Admin id={} deleted product id={}", command.callerId, existing.id)
        meterRegistry.counter(METRIC_DELETE, "result", "success").increment()
    }

    /** CAT-006's shared admin-only gate — throws before any target lookup happens. */
    private fun requireAdmin(callerId: UUID, callerRole: RoleCode, metricName: String, forbiddenMessage: String) {
        if (callerRole != RoleCode.ADMIN) {
            logger.warn("Forbidden: caller id={} role={} attempted an admin-only product action", callerId, callerRole.dbCode)
            meterRegistry.counter(metricName, "result", "forbidden").increment()
            throw ForbiddenActionException(forbiddenMessage)
        }
    }

    private fun findByIdOrThrow(id: UUID, metricName: String): Product =
        productRepository.findById(id) ?: run {
            logger.warn("Admin action on nonexistent product id={}", id)
            meterRegistry.counter(metricName, "result", "not_found").increment()
            throw ProductNotFoundException(id)
        }

    /**
     * CAT-001: name/description/category must stay non-blank. [field] is
     * null when the caller omitted it from the PATCH (leave unchanged,
     * handled by the `?:` at each call site); non-null-but-blank is a
     * caller error (see ProductValidationException kdoc for why this can't
     * be a `@NotBlank` on the DTO itself).
     */
    private fun requireNonBlankIfPresent(field: String?, fieldName: String): String? {
        if (field != null && field.isBlank()) {
            throw ProductValidationException("$fieldName must not be blank")
        }
        return field
    }

    /**
     * CAT-002: unit price must be strictly greater than zero. [raw] is
     * parsed here, rather than validated as a numeric DTO field, so a
     * zero/negative/unparseable price gets the Phase-3 doc's specific
     * INVALID_PRICE code instead of a generic VALIDATION_ERROR.
     */
    private fun parseUnitPrice(raw: String): BigDecimal {
        val price = try {
            BigDecimal(raw)
        } catch (malformed: NumberFormatException) {
            throw InvalidPriceException("unitPrice must be a valid decimal number.")
        }
        if (price <= BigDecimal.ZERO) {
            throw InvalidPriceException("unitPrice must be greater than zero.")
        }
        return price
    }

    private companion object {
        const val METRIC_CREATE = "catalog.products.create"
        const val METRIC_LIST = "catalog.products.list"
        const val METRIC_VIEW = "catalog.products.view"
        const val METRIC_UPDATE = "catalog.products.update"
        const val METRIC_UPDATE_STATUS = "catalog.products.update_status"
        const val METRIC_DELETE = "catalog.products.delete"

        // Exact wording fixed by the Phase-3 doc's 403 response example.
        const val MESSAGE_CREATE_FORBIDDEN = "Only an Administrator can create a product."

        // Not fixed by the Phase-4 doc (no 403 example shown for these three
        // endpoints) — this implementation's own judgment call, following
        // the same per-endpoint-specific-wording style Phase-3/Phase-2 used
        // where the doc did fix wording.
        const val MESSAGE_UPDATE_FORBIDDEN = "Only an Administrator can update a product."
        const val MESSAGE_STATUS_FORBIDDEN = "Only an Administrator can change a product's status."
        const val MESSAGE_DELETE_FORBIDDEN = "Only an Administrator can delete a product."
    }
}
