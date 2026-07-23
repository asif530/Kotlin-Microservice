package com.minimart.catalog.infrastructure.grpc

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for the internal gRPC server (Phase 5:
 * order-service calling `CatalogService.GetProduct`/`ReserveStock`/
 * `ReleaseStock`, ARCHITECTURE.md §4). Bound from `catalog.grpc.*` in
 * application.yml.
 *
 * @property port TCP port the gRPC server binds to. Separate from
 *   [server.port] (the REST port, 8082).
 */
@ConfigurationProperties(prefix = "catalog.grpc")
data class GrpcServerProperties(
    val port: Int,
)
