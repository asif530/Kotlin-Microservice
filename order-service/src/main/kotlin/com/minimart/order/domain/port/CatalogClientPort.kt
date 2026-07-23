package com.minimart.order.domain.port

import java.math.BigDecimal
import java.util.UUID

/**
 * A product's checkout-time facts, as reported by catalog-service's gRPC
 * `GetProduct` — the source of ORD-005's name/price snapshot.
 */
data class ProductSnapshot(
    val productId: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val stockAvailable: Int,
)

/**
 * Outbound port for the Phase-5 gRPC calls to catalog-service
 * (`CatalogService.GetProduct`/`ReserveStock`/`ReleaseStock`) —
 * CAT-011/ORD-007/ORD-008/ORD-012. Implemented by
 * infrastructure.grpc.CatalogGrpcClientAdapter.
 */
interface CatalogClientPort {

    /**
     * @return the product's current name/price/stock, or `null` if it
     *   doesn't exist or is Deactivated (CAT-008) — catalog-service's
     *   GetProduct already collapses both cases; see that gRPC server's
     *   kdoc. A `null` here is what lets order-service report
     *   INSUFFICIENT_STOCK (available: 0) for a Deactivated product,
     *   satisfying the Phase-5 doc's regression note.
     */
    fun getProduct(productId: UUID): ProductSnapshot?

    /** @return true if the atomic reservation succeeded (ORD-007/ORD-008/CAT-011/GEN-001). */
    fun reserveStock(productId: UUID, quantity: Int): Boolean

    /**
     * The compensating action for a reservation that must be undone —
     * either this phase's own per-order all-or-nothing rollback (ORD-007)
     * or a later order cancellation (ORD-012).
     *
     * @return true if the release succeeded. A caller that gets `false`
     *   back should log loudly with full order/customer context (a stock
     *   count that should have been given back but wasn't is a real data
     *   integrity concern) rather than treat it as routine — this port
     *   deliberately doesn't swallow that signal itself, since the caller
     *   has the context (which order attempt, which customer) to make
     *   that log line actually useful.
     */
    fun releaseStock(productId: UUID, quantity: Int): Boolean
}
