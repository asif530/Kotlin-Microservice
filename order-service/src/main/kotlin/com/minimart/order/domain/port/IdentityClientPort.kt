package com.minimart.order.domain.port

import java.util.UUID

/**
 * Outbound port for the Phase-5 gRPC call to identity-service
 * (`IdentityService.GetUser`) — ORD-001: only an authenticated, Active
 * Customer can place an order. Implemented by
 * infrastructure.grpc.IdentityGrpcClientAdapter.
 *
 * Deliberately returns a single boolean rather than exposing the
 * account's raw existence/active fields separately: order-service only
 * ever needs "is this customer allowed to place an order right now", and
 * ACC-005's "identical error, no leaked detail" posture (mirrored here by
 * the Phase-5 doc's NOT_ELIGIBLE_TO_ORDER wording) means a missing account
 * and a Deactivated account must be indistinguishable to the caller —
 * collapsing both into one boolean at the port boundary makes that the
 * only thing callers of this port can even express.
 */
interface IdentityClientPort {

    /** True only if [customerId] resolves to a real, Active identity-service account. */
    fun isEligibleToOrder(customerId: UUID): Boolean
}
