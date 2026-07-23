package com.minimart.order.infrastructure.idempotency

import com.minimart.order.domain.port.IdempotencyPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Redis-backed implementation of ORD-009's fast-path idempotency cache —
 * `order:idem:{idempotency-key}` -> order id, 24h TTL, exactly the key
 * pattern and lifetime ARCHITECTURE.md §9's Redis table specifies. See
 * domain.port.IdempotencyPort's kdoc for why this is a speed optimization
 * only, not the actual correctness guarantee.
 */
@Component
class RedisIdempotencyAdapter(
    private val redisTemplate: StringRedisTemplate,
) : IdempotencyPort {

    override fun findOrderId(idempotencyKey: String): UUID? =
        redisTemplate.opsForValue().get(keyFor(idempotencyKey))?.let { UUID.fromString(it) }

    override fun remember(idempotencyKey: String, orderId: UUID) {
        redisTemplate.opsForValue().set(keyFor(idempotencyKey), orderId.toString(), TTL)
    }

    private fun keyFor(idempotencyKey: String) = "order:idem:$idempotencyKey"

    private companion object {
        val TTL: Duration = Duration.ofHours(24)
    }
}
