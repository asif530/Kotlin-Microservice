package com.minimart.order.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA repository. Intentionally package-visible to the
 * infrastructure layer only via [OrderRepositoryAdapter] — the application
 * layer never sees this interface, only
 * com.minimart.order.domain.port.OrderRepositoryPort.
 */
interface SpringDataOrderJpaRepository : JpaRepository<OrderJpaEntity, UUID> {

    fun findByIdempotencyKey(idempotencyKey: String): OrderJpaEntity?

    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<OrderJpaEntity>

    fun findAllByOrderByCreatedAtDesc(): List<OrderJpaEntity>
}
