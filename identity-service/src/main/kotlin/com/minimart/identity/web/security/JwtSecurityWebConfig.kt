package com.minimart.identity.web.security

import com.minimart.identity.domain.port.TokenVerifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Registers [JwtAuthenticationFilter], scoped to paths under /api/users only.
 *
 * Deliberately a *plain* `@Configuration` — not `WebMvcConfigurer` — and
 * kept in its own class rather than merged with
 * [CallerPrincipalArgumentResolverConfig]. Spring MVC assembles the
 * `handlerExceptionResolver` bean this class injects by calling every
 * registered `WebMvcConfigurer`'s `configureHandlerExceptionResolvers`/
 * `extendHandlerExceptionResolvers` hooks; a class that both implements
 * `WebMvcConfigurer` *and* injects `HandlerExceptionResolver` in its own
 * constructor creates an unresolvable circular dependency (confirmed the
 * hard way — the first version of this file did exactly that and failed
 * every integration test with `BeanCurrentlyInCreationException`). Splitting
 * the filter registration (needs the resolver) from the argument-resolver
 * registration (needs to *be* a `WebMvcConfigurer`) breaks the cycle.
 *
 * [JwtAuthenticationFilter] itself is built here as a plain object, not an
 * additional `@Component`, so Spring Boot's automatic filter-bean
 * registration (which would default to matching every path) never runs for
 * it — this explicit /api/users-scoped [FilterRegistrationBean] is the only
 * registration that exists.
 */
@Configuration
class JwtSecurityWebConfig(
    private val tokenVerifier: TokenVerifier,
    @Qualifier("handlerExceptionResolver") private val handlerExceptionResolver: HandlerExceptionResolver,
) {

    @Bean
    fun jwtAuthenticationFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(JwtAuthenticationFilter(tokenVerifier, handlerExceptionResolver))
        registration.urlPatterns = listOf("/api/users/*")
        registration.order = Ordered.HIGHEST_PRECEDENCE + 10
        return registration
    }
}
