package com.minimart.order.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Maps the `order_status` lookup table seeded by V2__seed_order_statuses.sql.
 * `id`/`code` are never written by application code — reference data only
 * (see com.minimart.order.domain.model.OrderStatus for the fixed values
 * this mirrors), same convention as identity-service's RoleJpaEntity.
 */
@Entity
@Table(name = "order_status")
class OrderStatusJpaEntity(
    @Id
    @Column(name = "id")
    val id: Short,

    @Column(name = "code", nullable = false, unique = true, length = 10)
    val code: String,
)
