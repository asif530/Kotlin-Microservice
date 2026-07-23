package com.minimart.order.infrastructure.grpc

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for order-service's two Phase-5 gRPC clients
 * (ARCHITECTURE.md §4). No service discovery layer (Consul) is wired up
 * anywhere in this project yet — every other cross-process address in this
 * codebase (Postgres, Mongo, Redis, RabbitMQ host/port) is a plain
 * configurable host/port too, so direct host/port config is the existing
 * convention this follows, not a shortcut invented for gRPC specifically.
 *
 * @property identityHost/identityPort where identity-service's gRPC server
 *   listens (see identity-service's `identity.grpc.port`, default 9081).
 * @property catalogHost/catalogPort where catalog-service's gRPC server
 *   listens (see catalog-service's `catalog.grpc.port`, default 9082).
 */
@ConfigurationProperties(prefix = "order.grpc")
data class GrpcClientProperties(
    val identityHost: String,
    val identityPort: Int,
    val catalogHost: String,
    val catalogPort: Int,
)
