package com.minimart.identity.application.testsupport

import com.minimart.identity.domain.model.Account
import com.minimart.identity.domain.port.IssuedToken
import com.minimart.identity.domain.port.TokenIssuer

/** Records the last account it was asked to issue a token for, for assertions. */
class FakeTokenIssuer : TokenIssuer {

    var lastIssuedFor: Account? = null
        private set

    override fun issue(account: Account): IssuedToken {
        lastIssuedFor = account
        return IssuedToken(token = "fake-token-for-${account.id}", tokenType = "Bearer", expiresInSeconds = 3600)
    }
}
