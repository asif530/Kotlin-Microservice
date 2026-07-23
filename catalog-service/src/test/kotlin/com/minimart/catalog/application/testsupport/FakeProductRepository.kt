package com.minimart.catalog.application.testsupport

import com.minimart.catalog.domain.model.Product
import com.minimart.catalog.domain.model.ProductStatus
import com.minimart.catalog.domain.port.ProductRepositoryPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for ProductRepositoryPort. Mirrors identity-service's FakeAccountRepository style. */
class FakeProductRepository : ProductRepositoryPort {

    private val productsById = ConcurrentHashMap<UUID, Product>()

    override fun insert(product: Product): Product {
        productsById[product.id] = product
        return product
    }

    override fun findVisibleById(id: UUID): Product? =
        productsById[id]?.takeIf { it.status == ProductStatus.ACTIVE }

    override fun searchVisible(category: String?): List<Product> =
        productsById.values
            .filter { it.status == ProductStatus.ACTIVE }
            .filter { category == null || it.category == category }

    override fun findById(id: UUID): Product? = productsById[id]

    override fun update(product: Product): Product {
        productsById[product.id] = product
        return product
    }

    override fun deleteById(id: UUID) {
        productsById.remove(id)
    }
}
