package com.minimart.order.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * JPA mapping for the `order_items` table (see V1__create_order_schema.sql).
 *
 * `line_total` is deliberately NOT mapped here — it's a Postgres
 * `GENERATED ALWAYS AS (...) STORED` column, and this domain never needs
 * to read it back through JPA: com.minimart.order.domain.model.OrderItem
 * computes the identical value itself (`unitPriceSnapshot * quantity`,
 * ORD-006). Omitting the field entirely means Hibernate never attempts to
 * write to a column the database itself owns, with no `insertable = false`/
 * `@Generated` machinery needed.
 */
@Entity
@Table(name = "order_items")
class OrderItemJpaEntity(
    @Id
    @Column(name = "id")
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    var order: OrderJpaEntity,

    @Column(name = "product_id", nullable = false)
    val productId: UUID,

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    val productNameSnapshot: String,

    @Column(name = "unit_price_snapshot", nullable = false)
    val unitPriceSnapshot: BigDecimal,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,
)
