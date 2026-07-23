package com.minimart.order.application.testsupport

import com.minimart.order.domain.port.IdentityClientPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for IdentityClientPort. */
class FakeIdentityClientPort : IdentityClientPort {

    private val eligibleCustomerIds = ConcurrentHashMap.newKeySet<UUID>()

    fun markEligible(customerId: UUID) {
        eligibleCustomerIds.add(customerId)
    }

    override fun isEligibleToOrder(customerId: UUID): Boolean = customerId in eligibleCustomerIds
}
