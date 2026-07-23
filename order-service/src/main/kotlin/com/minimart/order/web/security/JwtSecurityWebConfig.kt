package com.minimart.order.web.security

import com.minimart.order.domain.port.TokenVerifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Registers [JwtAuthenticationFilter], scoped to paths under /api/orders
 * only. Two url patterns are needed for the same reason
 * catalog-service's own JwtSecurityWebConfig needs two — POST /api/orders
 * and GET /api/orders are the bare `/api/orders` path itself, with no
 * trailing segment for a wildcard suffix to match.
 */
@Configuration
class JwtSecurityWebConfig(
    private val tokenVerifier: TokenVerifier,
    @Qualifier("handlerExceptionResolver") private val handlerExceptionResolver: HandlerExceptionResolver,
) {

    @Bean
    fun jwtAuthenticationFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(JwtAuthenticationFilter(tokenVerifier, handlerExceptionResolver))
        registration.urlPatterns = listOf("/api/orders", "/api/orders/*")
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        return registration
    }
}
