package com.minimart.order.domain.exception

/**
 * Raised by OrderRepositoryPort.insert when Postgres's `UNIQUE` constraint
 * on `idempotency_key` rejects a concurrent duplicate submission that slid
 * past the Redis fast-path check (domain.port.IdempotencyPort) — the race
 * window ORD-009 has to actually close, since Redis alone cannot (two
 * near-simultaneous requests can both miss the same not-yet-written cache
 * key). The caller (application.OrderService) responds to this by
 * releasing whatever stock this losing attempt reserved and returning the
 * winning attempt's order instead — this is an internal control-flow
 * signal, not something that ever reaches the HTTP layer as its own error
 * code.
 */
class DuplicateIdempotencyKeyException(val idempotencyKey: String) : RuntimeException(
    "An order with idempotency key '$idempotencyKey' already exists.",
)
