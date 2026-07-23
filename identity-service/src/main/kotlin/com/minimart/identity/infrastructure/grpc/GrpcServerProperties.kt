package com.minimart.identity.infrastructure.grpc

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for the internal gRPC server (Phase 5:
 * order-service calling `IdentityService.GetUser`, ARCHITECTURE.md §4).
 * Bound from `identity.grpc.*` in application.yml.
 *
 * @property port TCP port the gRPC server binds to. Separate from
 *   [server.port] (the REST port, 8081) — gRPC and Spring MVC's servlet
 *   container are two independent network listeners in the same process.
 */
@ConfigurationProperties(prefix = "identity.grpc")
data class GrpcServerProperties(
    val port: Int,
)
