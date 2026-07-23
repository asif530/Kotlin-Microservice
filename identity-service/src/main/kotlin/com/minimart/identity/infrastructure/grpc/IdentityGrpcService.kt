package com.minimart.identity.infrastructure.grpc

import com.minimart.identity.domain.model.AccountStatus
import com.minimart.identity.domain.port.AccountRepositoryPort
import com.minimart.identity.grpc.GetUserRequest
import com.minimart.identity.grpc.IdentityServiceGrpcKt
import com.minimart.identity.grpc.UserResponse
import com.minimart.identity.grpc.userResponse
import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * gRPC server implementation of `IdentityService.GetUser` (identity.proto) —
 * Phase-5's cross-service read: order-service calls this to confirm the
 * buyer exists and is Active before placing an order (ORD-001).
 *
 * Deliberately talks to [AccountRepositoryPort] directly rather than
 * through UserAccountUseCase: this is a plain, unauthorized read of public
 * account-existence facts (no admin/self distinction applies to "does this
 * account exist and is it active" — that's the whole of what GetUser
 * answers), unlike Phase-2's endpoints which layer ACC-008/009/011's
 * authorization on top of the same repository.
 */
@Component
class IdentityGrpcService(
    private val accountRepository: AccountRepositoryPort,
) : IdentityServiceGrpcKt.IdentityServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(IdentityGrpcService::class.java)

    override suspend fun getUser(request: GetUserRequest): UserResponse {
        val accountId = try {
            UUID.fromString(request.userId)
        } catch (notAUuid: IllegalArgumentException) {
            logger.warn("GetUser rejected: '{}' is not a valid account id", request.userId)
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("user_id is not a valid UUID"))
        }

        val account = accountRepository.findById(accountId) ?: run {
            logger.info("GetUser: no account found for id={}", accountId)
            throw StatusException(Status.NOT_FOUND.withDescription("No account with this id"))
        }

        return userResponse {
            userId = account.id.toString()
            email = account.email
            fullName = account.fullName
            active = account.status == AccountStatus.ACTIVE
        }
    }
}
