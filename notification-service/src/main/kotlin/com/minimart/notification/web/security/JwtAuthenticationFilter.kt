package com.minimart.notification.web.security

import com.minimart.notification.domain.exception.UnauthenticatedException
import com.minimart.notification.domain.port.TokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Servlet-level gate for GET /api/notifications — scoped to that path via
 * [JwtSecurityWebConfig]'s `FilterRegistrationBean`. Every route in this
 * service requires authentication (kong.decl.yaml's `notifications-list`
 * route carries the jwt plugin, no public bypass), so this mirrors
 * identity-service/order-service's uniform-auth filter, not
 * catalog-service's mixed public/private one.
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
        const val CALLER_PRINCIPAL_ATTRIBUTE = "com.minimart.notification.CALLER_PRINCIPAL"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
