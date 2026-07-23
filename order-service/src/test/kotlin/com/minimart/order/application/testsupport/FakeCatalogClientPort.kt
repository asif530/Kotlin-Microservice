package com.minimart.order.application.testsupport

import com.minimart.order.domain.port.CatalogClientPort
import com.minimart.order.domain.port.ProductSnapshot
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for CatalogClientPort — simulates catalog-service's product/stock state. */
class FakeCatalogClientPort : CatalogClientPort {

    private data class FakeProduct(val name: String, val unitPrice: BigDecimal, var stock: Int, val visible: Boolean)

    private val products = ConcurrentHashMap<UUID, FakeProduct>()

    /** Test hook: forces the next reserveStock call for [productId] to fail regardless of stock (simulates a race, GEN-001). */
    private val forcedReserveFailures = ConcurrentHashMap.newKeySet<UUID>()

    fun seedProduct(productId: UUID, name: String, unitPrice: String, stock: Int, visible: Boolean = true) {
        products[productId] = FakeProduct(name, BigDecimal(unitPrice), stock, visible)
    }

    fun forceReserveFailureFor(productId: UUID) {
        forcedReserveFailures += productId
    }

    override fun getProduct(productId: UUID): ProductSnapshot? {
        val product = products[productId]?.takeIf { it.visible } ?: return null
        return ProductSnapshot(productId, product.name, product.unitPrice, product.stock)
    }

    override fun reserveStock(productId: UUID, quantity: Int): Boolean {
        if (productId in forcedReserveFailures) {
            forcedReserveFailures.remove(productId)
            return false
        }
        val product = products[productId]?.takeIf { it.visible } ?: return false
        if (product.stock < quantity) return false
        product.stock -= quantity
        return true
    }

    override fun releaseStock(productId: UUID, quantity: Int): Boolean {
        val product = products[productId]?.takeIf { it.visible } ?: return false
        product.stock += quantity
        return true
    }

    fun currentStock(productId: UUID): Int? = products[productId]?.stock
}
