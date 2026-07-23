package com.minimart.order.infrastructure.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Creates the two long-lived gRPC channels order-service's checkout flow
 * uses. `usePlaintext()` — no TLS between services: ARCHITECTURE.md §7
 * explicitly states internal gRPC traffic is "not authenticated
 * service-to-service in this MVP," relying instead on the Docker network
 * boundary (only Kong is reachable from outside) as the actual security
 * control; service-to-service mTLS via Consul Connect is §13's explicitly
 * deferred roadmap item, not built here.
 *
 * `destroyMethod = "shutdown"` ties each channel's lifecycle to the Spring
 * context so tests (and real shutdowns) don't leak the underlying Netty
 * event-loop threads. Consumers select which channel they want via
 * `@Qualifier("identityGrpcChannel")`/`@Qualifier("catalogGrpcChannel")` on
 * the injection point (see IdentityGrpcClientAdapter/CatalogGrpcClientAdapter) —
 * a plain named-bean qualifier, not a custom annotation, since Spring
 * already supports this directly and a custom annotation would only add a
 * layer of indirection for no benefit here.
 */
@Configuration
class GrpcChannelConfig(
    private val grpcClientProperties: GrpcClientProperties,
) {

    @Bean("identityGrpcChannel", destroyMethod = "shutdown")
    fun identityGrpcChannel(): ManagedChannel =
        ManagedChannelBuilder.forAddress(grpcClientProperties.identityHost, grpcClientProperties.identityPort)
            .usePlaintext()
            .build()

    @Bean("catalogGrpcChannel", destroyMethod = "shutdown")
    fun catalogGrpcChannel(): ManagedChannel =
        ManagedChannelBuilder.forAddress(grpcClientProperties.catalogHost, grpcClientProperties.catalogPort)
            .usePlaintext()
            .build()
}
