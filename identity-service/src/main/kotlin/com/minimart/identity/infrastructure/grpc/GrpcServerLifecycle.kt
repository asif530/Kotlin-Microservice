package com.minimart.identity.infrastructure.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Starts/stops the internal gRPC server as a Spring [SmartLifecycle] bean —
 * ARCHITECTURE.md §4's explicitly recommended wiring ("the gRPC Server
 * started/stopped from a Spring SmartLifecycle bean"), deliberately without
 * a third-party Spring-gRPC starter (same doc, same reasoning: unverified
 * Spring Boot 4 compatibility vs. a well-understood ~20-line integration
 * with the official grpc-java library).
 *
 * `ServerBuilder.forPort` (not `NettyServerBuilder` directly) resolves to
 * the Netty transport via `io.grpc:grpc-netty`'s `ServerProvider`
 * (`ServiceLoader`-discovered) — this is the standard, transport-agnostic
 * way to build a gRPC server when exactly one transport implementation is
 * on the classpath, matching how `identity-service` already avoids
 * transport-specific imports elsewhere.
 *
 * The reflection service (`grpc-services`, already a declared dependency —
 * see build.gradle.kts) is registered alongside the real service so tools
 * like `grpcurl`/`grpcui` can introspect this server without a checked-in
 * `.proto` copy; a small, free addition given the dependency already exists.
 */
@Component
class GrpcServerLifecycle(
    private val grpcServerProperties: GrpcServerProperties,
    private val identityGrpcService: IdentityGrpcService,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(GrpcServerLifecycle::class.java)
    private var server: Server? = null

    override fun start() {
        val builtServer = ServerBuilder.forPort(grpcServerProperties.port)
            .addService(identityGrpcService)
            .addService(ProtoReflectionServiceV1.newInstance())
            .build()
            .start()
        server = builtServer
        logger.info("gRPC server started on port {}", grpcServerProperties.port)
    }

    override fun stop() {
        server?.let {
            it.shutdown()
            if (!it.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                it.shutdownNow()
            }
            logger.info("gRPC server stopped")
        }
        server = null
    }

    override fun isRunning(): Boolean = server?.isShutdown == false

    /**
     * Runs after the servlet container (default phase) so the REST health
     * endpoint is already up when this starts — not a hard dependency, just
     * a sensible ordering. Stops before the servlet container on shutdown
     * (SmartLifecycle stops in descending phase order).
     */
    override fun getPhase(): Int = Int.MAX_VALUE - 1

    private companion object {
        const val SHUTDOWN_TIMEOUT_SECONDS = 5L
    }
}
