package com.minimart.order.infrastructure.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA mapping for the `orders` table (see V1__create_order_schema.sql).
 *
 * `status` is deliberately `FetchType.EAGER` (JPA's own default for
 * `@ManyToOne`, kept explicit here rather than omitted) — unlike
 * identity-service's `AccountJpaEntity.role`, which is `LAZY` because it's
 * read one account at a time. GET /api/orders can return many rows in one
 * call; an eager `@ManyToOne` compiles to a single SQL `JOIN` Hibernate
 * generates automatically, whereas `LAZY` would mean one extra
 * per-row query (a real N+1) as order history grows. `items`, by contrast,
 * stays the JPA default `LAZY` — only GET /api/orders/{id}'s single-order
 * path ever needs it (see OrderRepositoryAdapter).
 */
@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    var status: OrderStatusJpaEntity,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: BigDecimal,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    val idempotencyKey: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var items: MutableList<OrderItemJpaEntity> = mutableListOf(),
)
