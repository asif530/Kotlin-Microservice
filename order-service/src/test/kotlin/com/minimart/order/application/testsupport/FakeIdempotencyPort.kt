package com.minimart.order.application.testsupport

import com.minimart.order.domain.port.IdempotencyPort
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test double for IdempotencyPort — no TTL simulated, since no test in this suite needs expiry. */
class FakeIdempotencyPort : IdempotencyPort {

    private val orderIdsByKey = ConcurrentHashMap<String, UUID>()

    override fun findOrderId(idempotencyKey: String): UUID? = orderIdsByKey[idempotencyKey]

    override fun remember(idempotencyKey: String, orderId: UUID) {
        orderIdsByKey[idempotencyKey] = orderId
    }
}
