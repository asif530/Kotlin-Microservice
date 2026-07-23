package com.minimart.catalog.application.testsupport

import com.minimart.catalog.domain.port.OrderHistoryPort
import java.util.UUID

/** In-memory test double for OrderHistoryPort — lets tests simulate CAT-009's "has order history" branch. */
class FakeOrderHistoryPort : OrderHistoryPort {

    private val productIdsWithHistory = mutableSetOf<UUID>()

    fun markAsOrdered(productId: UUID) {
        productIdsWithHistory.add(productId)
    }

    override fun hasOrderHistory(productId: UUID): Boolean = productId in productIdsWithHistory
}
