package com.minimart.catalog.domain.port

import java.util.UUID

/**
 * Outbound port for CAT-009's "has this product ever been part of a placed
 * order" question — the one real cross-service dependency Phase-4 has on
 * order-service data, per the Phase-4 doc's own note: "catalog-service has
 * to ask order-service (or a materialized flag kept in sync some other
 * way)... flagging it here rather than guessing at the mechanism."
 *
 * order-service does not exist yet (Phase 5) and ARCHITECTURE.md's gRPC
 * contract only runs in the opposite direction (order-service calls
 * catalog-service's GetProduct/ReserveStock, never the reverse) — there is
 * no wire contract today for catalog-service to ask this question for
 * real. Implemented for now by
 * infrastructure.external.NoOpOrderHistoryAdapter, a placeholder that
 * always answers "no history" so CAT-009's 409 branch has a real seam to
 * plug into rather than being silently skipped; see that class's kdoc for
 * what Phase 5 needs to replace it with.
 */
interface OrderHistoryPort {

    /** True if [productId] appears in at least one placed order's line items (CAT-009). */
    fun hasOrderHistory(productId: UUID): Boolean
}
