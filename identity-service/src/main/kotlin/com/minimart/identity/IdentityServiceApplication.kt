package com.minimart.identity

import com.minimart.identity.infrastructure.security.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties::class)
class IdentityServiceApplication

fun main(args: Array<String>) {
    runApplication<IdentityServiceApplication>(*args)
}
