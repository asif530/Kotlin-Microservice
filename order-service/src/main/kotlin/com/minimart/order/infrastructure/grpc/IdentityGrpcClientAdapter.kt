package com.minimart.order.infrastructure.grpc

import com.minimart.identity.grpc.IdentityServiceGrpcKt
import com.minimart.identity.grpc.getUserRequest
import com.minimart.order.domain.port.IdentityClientPort
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * gRPC client adapter for identity-service's `GetUser` (ORD-001). Bridges
 * from order-service's otherwise-synchronous call chain (Spring MVC
 * controller -> OrderService -> this adapter, none of it `suspend`, same
 * blocking style Phases 1-4 already use throughout) into the generated
 * coroutine stub's `suspend fun getUser` via `runBlocking` — a deliberate,
 * narrow use of coroutines only at this gRPC-call boundary
 * (ARCHITECTURE.md §3: "coroutines where calls are naturally async (gRPC
 * clients...)"), rather than rewriting order-service's REST/JPA layers
 * into `suspend` functions end to end, which this phase does not need.
 */
@Component
class IdentityGrpcClientAdapter(
    @Qualifier("identityGrpcChannel") channel: ManagedChannel,
) : IdentityClientPort {

    private val logger = LoggerFactory.getLogger(IdentityGrpcClientAdapter::class.java)
    private val stub = IdentityServiceGrpcKt.IdentityServiceCoroutineStub(channel)

    override fun isEligibleToOrder(customerId: UUID): Boolean = runBlocking {
        try {
            val response = stub.getUser(getUserRequest { userId = customerId.toString() })
            response.active
        } catch (notFound: StatusException) {
            if (notFound.status.code == Status.Code.NOT_FOUND) {
                logger.info("GetUser: no identity-service account for id={}", customerId)
                false
            } else {
                // Anything other than NOT_FOUND (UNAVAILABLE, DEADLINE_EXCEEDED, ...) is an
                // infrastructure problem, not a "this customer isn't eligible" business outcome —
                // collapsing it into `false` here would misreport an outage as a permission
                // denial. Let it propagate to the same catch-all 500 INTERNAL_ERROR every other
                // unexpected failure in this service already produces.
                throw notFound
            }
        }
    }
}
