package com.minimart.identity.infrastructure.persistence

import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.model.RoleCode

/** Maps the JPA entity to the domain model. Kept out of the domain layer on purpose. */
fun AccountJpaEntity.toDomain(): Account =
    Account(
        id = id,
        email = email,
        passwordHash = passwordHash,
        fullName = fullName,
        role = RoleCode.fromDbCode(role.code),
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
