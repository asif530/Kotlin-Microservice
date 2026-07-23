package com.minimart.catalog.infrastructure.external

import com.minimart.catalog.domain.port.OrderHistoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * ============================================================================
 * PLACEHOLDER — NOT A REAL CAT-009 CHECK. Every product looks order-free.
 * ============================================================================
 * order-service (Phase 5) doesn't exist yet: no `order_items` table, no gRPC
 * server, no wire contract for catalog-service to ask this question (see
 * domain.port.OrderHistoryPort kdoc — ARCHITECTURE.md's gRPC direction runs
 * order-service -> catalog-service, never the reverse). Rather than block
 * Phase-4's DELETE endpoint on an cross-service mechanism that can't be
 * built yet, or silently skip CAT-009 altogether, this adapter always
 * answers "no order history," which is *correct by construction* today —
 * no order has ever been placed against any product, because order-service
 * has no way to place one yet. DELETE therefore behaves exactly like a
 * real CAT-009 check would, for every product that currently exists.
 *
 * This must be replaced once Phase 5 exists. The real mechanism is an open
 * question the Phase-4 doc deliberately leaves unresolved ("a materialized
 * flag kept in sync some other way" is one option; a new reverse-direction
 * gRPC call is another) — do not treat this class's existence as evidence
 * that decision has already been made.
 */
@Component
class NoOpOrderHistoryAdapter : OrderHistoryPort {

    private val logger = LoggerFactory.getLogger(NoOpOrderHistoryAdapter::class.java)

    override fun hasOrderHistory(productId: UUID): Boolean {
        logger.debug("hasOrderHistory({}) — placeholder adapter, order-service does not exist yet; always false", productId)
        return false
    }
}
