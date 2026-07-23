package com.minimart.catalog

import com.minimart.catalog.infrastructure.grpc.GrpcServerProperties
import com.minimart.catalog.infrastructure.security.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class, GrpcServerProperties::class)
class CatalogServiceApplication

fun main(args: Array<String>) {
    runApplication<CatalogServiceApplication>(*args)
}
