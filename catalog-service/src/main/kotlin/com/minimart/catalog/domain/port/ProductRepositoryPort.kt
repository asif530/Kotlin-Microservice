package com.minimart.catalog.domain.port

import com.minimart.catalog.domain.model.Product
import java.util.UUID

/**
 * Outbound port (Clean Architecture / dependency inversion): the application
 * layer depends on this interface only, never on Spring Data MongoDB
 * directly. Implemented by infrastructure.persistence.ProductRepositoryAdapter.
 */
interface ProductRepositoryPort {

    /** Persists a newly created product (POST /api/products, CAT-006). */
    fun insert(product: Product): Product

    /**
     * Lookup by primary key, restricted to visible (ACTIVE) products only —
     * CAT-008: a Deactivated product must be treated as if it doesn't exist
     * to a browsing caller, so this deliberately returns null for a
     * Deactivated product exactly as it would for a missing id. Used by
     * GET /api/products/{id}.
     */
    fun findVisibleById(id: UUID): Product?

    /**
     * Browse/search, restricted to visible (ACTIVE) products only (CAT-008),
     * optionally narrowed to one category (CAT-006/CAT-007). Zero-stock
     * products are included — CAT-007 only requires them to be marked out
     * of stock, not excluded. Used by GET /api/products.
     */
    fun searchVisible(category: String?): List<Product>

    /**
     * Lookup by primary key with no status restriction — Phase-4's
     * admin-only mutation endpoints (PATCH .../status, PATCH /{id},
     * DELETE /{id}) act on a product regardless of whether it's currently
     * ACTIVE or Deactivated, unlike [findVisibleById]'s customer-facing
     * "Deactivated looks like missing" rule.
     */
    fun findById(id: UUID): Product?

    /**
     * Persists changes to an existing product's mutable fields (CAT-010:
     * name/description/category/unitPrice; CAT-008: status) — Phase-4's
     * admin edit/status endpoints. Never inserts a new document.
     */
    fun update(product: Product): Product

    /**
     * Permanently removes a product (CAT-009: only when it has never been
     * part of a placed order — the caller is responsible for that check
     * before calling this). Used by DELETE /api/products/{id}.
     */
    fun deleteById(id: UUID)

    /**
     * Atomically decrements [quantity] from an ACTIVE product's stock, but
     * only if it has at least that much available (CAT-011: catalog-service
     * is the sole authority on stock; ORD-008: a successful reservation
     * reduces stock immediately and permanently). Used by Phase-5's gRPC
     * `ReserveStock` (order-service's checkout flow).
     *
     * Must be a single atomic conditional update at the database level
     * (Mongo's own per-document atomicity, not a read-then-write from this
     * adapter) — that's what makes GEN-001 hold under concurrent checkouts
     * for the same product: two simultaneous callers can never both
     * successfully reserve more than what's actually available.
     *
     * @return true if the reservation succeeded (stock was decremented);
     *   false if the product doesn't exist, isn't ACTIVE, or doesn't have
     *   [quantity] available — the caller cannot tell which from this
     *   boolean alone, matching CAT-008's "Deactivated looks like
     *   out-of-stock to this call" posture.
     */
    fun reserveStock(id: UUID, quantity: Int): Boolean

    /**
     * Gives back [quantity] to an ACTIVE product's stock — the compensating
     * action for a reservation that must be undone (Phase-5's per-order
     * all-or-nothing rollback, ORD-007) or an order cancellation (ORD-012).
     * No lower-bound condition applies here (unlike [reserveStock]) — adding
     * stock back can never itself violate CAT-011's non-negative invariant.
     *
     * @return true if a matching ACTIVE product was found and updated;
     *   false if no such product exists. A caller releasing stock for a
     *   product id it just reserved from should treat `false` here as a
     *   serious inconsistency worth logging loudly, not a normal outcome.
     */
    fun releaseStock(id: UUID, quantity: Int): Boolean
}
