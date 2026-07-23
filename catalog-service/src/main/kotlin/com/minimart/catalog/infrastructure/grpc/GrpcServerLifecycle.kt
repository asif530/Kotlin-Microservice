package com.minimart.catalog.infrastructure.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Starts/stops the internal gRPC server as a Spring [SmartLifecycle] bean —
 * mirrors identity-service's own GrpcServerLifecycle exactly (see that
 * class's kdoc for the full rationale: ARCHITECTURE.md §4's recommended
 * wiring, no third-party Spring-gRPC starter, `ServerBuilder.forPort`
 * resolving to the Netty transport via `io.grpc:grpc-netty`'s
 * `ServiceLoader`-discovered provider).
 */
@Component
class GrpcServerLifecycle(
    private val grpcServerProperties: GrpcServerProperties,
    private val catalogGrpcService: CatalogGrpcService,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(GrpcServerLifecycle::class.java)
    private var server: Server? = null

    override fun start() {
        val builtServer = ServerBuilder.forPort(grpcServerProperties.port)
            .addService(catalogGrpcService)
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

    override fun getPhase(): Int = Int.MAX_VALUE - 1

    private companion object {
        const val SHUTDOWN_TIMEOUT_SECONDS = 5L
    }
}
