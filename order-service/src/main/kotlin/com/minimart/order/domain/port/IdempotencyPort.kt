package com.minimart.order.domain.port

import java.util.UUID

/**
 * Outbound port for ORD-009's fast-path retry guard (`order:idem:{key}` in
 * Redis, ARCHITECTURE.md §9, 24h TTL) — implemented by
 * infrastructure.idempotency.RedisIdempotencyAdapter.
 *
 * This is a best-effort speed optimization only, mirroring
 * identity-service's `existsByEmail`-vs-`save` split: it lets a retried
 * checkout skip re-running the identity/catalog gRPC calls entirely, but
 * it is never the actual correctness guarantee — Postgres's `UNIQUE`
 * constraint on `idempotency_key` (OrderRepositoryPort.insert) is the
 * authoritative guard a race or an expired cache entry falls back on.
 */
interface IdempotencyPort {

    /** The order id already created for [idempotencyKey], or null on a cache miss (first attempt, or TTL expired). */
    fun findOrderId(idempotencyKey: String): UUID?

    /** Records that [idempotencyKey] produced [orderId], for 24h. */
    fun remember(idempotencyKey: String, orderId: UUID)
}
