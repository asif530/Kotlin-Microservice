package com.minimart.notification.web.security

import com.minimart.notification.domain.port.TokenVerifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Registers [JwtAuthenticationFilter], scoped to /api/notifications only —
 * a single bare path, no nested segments or path variables (there's only
 * ever one endpoint in this service), unlike catalog-service/order-service's
 * two-pattern registration.
 */
@Configuration
class JwtSecurityWebConfig(
    private val tokenVerifier: TokenVerifier,
    @Qualifier("handlerExceptionResolver") private val handlerExceptionResolver: HandlerExceptionResolver,
) {

    @Bean
    fun jwtAuthenticationFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(JwtAuthenticationFilter(tokenVerifier, handlerExceptionResolver))
        registration.urlPatterns = listOf("/api/notifications")
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        return registration
    }
}
