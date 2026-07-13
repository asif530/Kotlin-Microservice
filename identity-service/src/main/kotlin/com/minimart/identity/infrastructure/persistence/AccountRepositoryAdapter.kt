package com.minimart.identity.infrastructure.persistence

import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.port.AccountRepositoryPort
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Adapter implementing the domain's outbound port on top of Spring Data
 * JPA. This is the only place in the codebase allowed to know that accounts
 * are stored in Postgres via Hibernate.
 *
 * Class-level `readOnly` transaction: with `spring.jpa.open-in-view: false`
 * (deliberately set — see application.yml), the Hibernate session closes as
 * soon as the repository call returns unless a transaction is explicitly
 * held open here. [findByEmail]'s mapping to the domain model reads
 * `role.code` off a lazy `@ManyToOne` proxy (AccountJpaEntity.role), which
 * needs an open session to initialize; wrapping the method in a transaction
 * keeps the session open for exactly the duration of that mapping, without
 * resorting to open-in-view (which would hold a session open for the whole
 * HTTP request, including the controller/view-rendering layer).
 */
@Repository
@Transactional(readOnly = true)
class AccountRepositoryAdapter(
    private val jpaRepository: SpringDataAccountJpaRepository,
    private val entityManager: EntityManager,
) : AccountRepositoryPort {

    private val logger = LoggerFactory.getLogger(AccountRepositoryAdapter::class.java)

    override fun existsByEmail(email: String): Boolean = jpaRepository.existsByEmail(email)

    override fun findByEmail(email: String): Account? = jpaRepository.findByEmail(email)?.toDomain()

    @Transactional
    override fun save(account: Account): Account {
        // Reference-only load (no SELECT) — role_id is fixed, seeded reference
        // data (V2__seed_roles.sql), so we only need a proxy to satisfy the FK.
        val roleReference = entityManager.getReference(RoleJpaEntity::class.java, account.role.dbId)

        val entity = AccountJpaEntity(
            id = account.id,
            email = account.email,
            passwordHash = account.passwordHash,
            fullName = account.fullName,
            role = roleReference,
            status = account.status,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )

        return try {
            // saveAndFlush (not save) forces the INSERT — and therefore the
            // `citext` unique-constraint check — to happen inside this
            // try/catch, rather than being deferred to end-of-transaction
            // flush where this method could no longer catch it.
            jpaRepository.saveAndFlush(entity).toDomain()
        } catch (constraintViolation: DataIntegrityViolationException) {
            logger.debug("Unique constraint violation persisting account", constraintViolation)
            throw EmailAlreadyRegisteredException(account.email)
        }
    }
}
