package com.minimart.order.testsupport.grpc

import com.minimart.identity.grpc.GetUserRequest
import com.minimart.identity.grpc.IdentityServiceGrpcKt
import com.minimart.identity.grpc.UserResponse
import com.minimart.identity.grpc.userResponse
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A real (in-process-network, not a Kotlin interface fake) gRPC server
 * standing in for identity-service in order-service's integration test —
 * this is what actually exercises IdentityGrpcClientAdapter's real
 * protobuf marshalling + `runBlocking` bridge, not just a hand-written
 * Kotlin double for IdentityClientPort. Started on an OS-assigned free
 * port (`forPort(0)`); [port] reads back the real bound port after
 * [start].
 */
class FakeIdentityGrpcServer : IdentityServiceGrpcKt.IdentityServiceCoroutineImplBase() {

    private val activeAccountIds = ConcurrentHashMap.newKeySet<UUID>()
    private val deactivatedAccountIds = ConcurrentHashMap.newKeySet<UUID>()
    private var server: Server? = null

    val port: Int get() = requireNotNull(server) { "Server not started" }.port

    fun markActive(accountId: UUID) {
        activeAccountIds += accountId
    }

    fun markDeactivated(accountId: UUID) {
        deactivatedAccountIds += accountId
    }

    fun start() {
        server = ServerBuilder.forPort(0).addService(this).build().start()
    }

    fun stop() {
        server?.shutdownNow()
    }

    override suspend fun getUser(request: GetUserRequest): UserResponse {
        val accountId = UUID.fromString(request.userId)
        return when {
            accountId in activeAccountIds -> userResponse {
                userId = accountId.toString()
                email = "test@example.test"
                fullName = "Test Customer"
                active = true
            }
            accountId in deactivatedAccountIds -> userResponse {
                userId = accountId.toString()
                email = "test@example.test"
                fullName = "Test Customer"
                active = false
            }
            else -> throw StatusException(Status.NOT_FOUND.withDescription("No account with this id"))
        }
    }
}
