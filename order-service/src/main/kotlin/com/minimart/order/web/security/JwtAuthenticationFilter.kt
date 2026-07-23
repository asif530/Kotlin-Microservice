package com.minimart.order.web.security

import com.minimart.order.domain.exception.UnauthenticatedException
import com.minimart.order.domain.port.TokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Servlet-level gate for every request under /api/orders — scoped to that
 * path via [JwtSecurityWebConfig]'s `FilterRegistrationBean`. Unlike
 * catalog-service's mixed public/private /api/products, every
 * /api/orders route requires authentication (kong.decl.yaml's
 * `order-create`/`order-list`/`order-detail`/`order-cancel` routes all
 * carry the jwt plugin) — so this filter, like identity-service's original
 * one, applies to every HTTP method with no GET/OPTIONS bypass.
 */
class JwtAuthenticationFilter(
    private val tokenVerifier: TokenVerifier,
    private val handlerExceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authorizationHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Rejected {} {}: missing or malformed Authorization header", request.method, request.requestURI)
            handlerExceptionResolver.resolveException(request, response, null, UnauthenticatedException())
            return
        }

        val token = authorizationHeader.removePrefix(BEARER_PREFIX)
        try {
            val caller = tokenVerifier.verify(token)
            request.setAttribute(CALLER_PRINCIPAL_ATTRIBUTE, caller)
            filterChain.doFilter(request, response)
        } catch (unauthenticated: UnauthenticatedException) {
            handlerExceptionResolver.resolveException(request, response, null, unauthenticated)
        }
    }

    companion object {
        /** Request-attribute key [CallerPrincipalArgumentResolver] reads back. */
        const val CALLER_PRINCIPAL_ATTRIBUTE = "com.minimart.order.CALLER_PRINCIPAL"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
