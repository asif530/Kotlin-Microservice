package com.minimart.identity.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA repository. Intentionally package-visible to the
 * infrastructure layer only via [AccountRepositoryAdapter] — the
 * application layer never sees this interface, only
 * com.minimart.identity.domain.port.AccountRepositoryPort.
 */
interface SpringDataAccountJpaRepository : JpaRepository<AccountJpaEntity, UUID> {

    /** Case-insensitive via the `citext` column type — see AccountJpaEntity kdoc. */
    fun existsByEmail(email: String): Boolean

    /** Case-insensitive via the `citext` column type — see AccountJpaEntity kdoc. */
    fun findByEmail(email: String): AccountJpaEntity?
}
