package com.minimart.identity.application.testsupport

import com.minimart.identity.domain.exception.EmailAlreadyRegisteredException
import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.port.AccountRepositoryPort
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory test double for AccountRepositoryPort. Mirrors the real
 * adapter's contract: [save] is the authoritative duplicate-email guard
 * (case-insensitive, like the DB's `citext` constraint), not [existsByEmail].
 */
class FakeAccountRepository : AccountRepositoryPort {

    private val accountsByLowercaseEmail = ConcurrentHashMap<String, Account>()

    /** Test hook to simulate a duplicate email slipping past the pre-check (a race). */
    var forceRaceOnNextSave: Boolean = false

    override fun existsByEmail(email: String): Boolean = accountsByLowercaseEmail.containsKey(email.lowercase())

    override fun findByEmail(email: String): Account? = accountsByLowercaseEmail[email.lowercase()]

    override fun save(account: Account): Account {
        val key = account.email.lowercase()
        if (forceRaceOnNextSave || accountsByLowercaseEmail.containsKey(key)) {
            forceRaceOnNextSave = false
            throw EmailAlreadyRegisteredException(account.email)
        }
        accountsByLowercaseEmail[key] = account
        return account
    }
}
