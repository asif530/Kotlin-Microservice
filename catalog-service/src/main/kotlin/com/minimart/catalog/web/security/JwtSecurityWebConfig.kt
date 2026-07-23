package com.minimart.catalog.web.security

import com.minimart.catalog.domain.port.TokenVerifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Registers [JwtAuthenticationFilter], scoped to paths under /api/products
 * only. Two url patterns are needed — unlike identity-service's single
 * wildcard pattern under `/api/users/` (every route there sits below a
 * segment, `/me` or `/{id}/...`) — because POST /api/products is the bare
 * `/api/products` path itself, with no trailing segment for a wildcard
 * suffix to match.
 *
 * Deliberately a *plain* `@Configuration`, not `WebMvcConfigurer`, and kept
 * separate from [CallerPrincipalArgumentResolverConfig] — see
 * identity-service's JwtSecurityWebConfig kdoc for the circular-dependency
 * reason.
 */
@Configuration
class JwtSecurityWebConfig(
    private val tokenVerifier: TokenVerifier,
    @Qualifier("handlerExceptionResolver") private val handlerExceptionResolver: HandlerExceptionResolver,
) {

    @Bean
    fun jwtAuthenticationFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(JwtAuthenticationFilter(tokenVerifier, handlerExceptionResolver))
        registration.urlPatterns = listOf("/api/products", "/api/products/*")
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        return registration
    }
}
