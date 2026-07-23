package com.minimart.catalog.web.security

import com.minimart.catalog.domain.exception.UnauthenticatedException
import com.minimart.catalog.domain.port.TokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Servlet-level gate for requests under /api/products — scoped to that path
 * via [JwtSecurityWebConfig]'s `FilterRegistrationBean`, mirroring
 * identity-service's own JwtAuthenticationFilter (see that class's kdoc for
 * why a plain `OncePerRequestFilter` was chosen over the full
 * spring-boot-starter-security filter chain).
 *
 * Unlike identity-service's /api/users/ (every route there requires
 * authentication), /api/products is a mixed path: CAT-006 makes GET
 * (list/detail) public browsing with no token at all, while POST (create)
 * is admin only. Kong's own routing already encodes this exact split
 * (kong.decl.yaml's `catalog-products-list`/`catalog-product-detail` routes
 * carry no jwt plugin; `catalog-products-create` does) — GET/HEAD/OPTIONS
 * are let through here unchallenged so this filter enforces the identical
 * policy for any caller reaching catalog-service directly, not just through
 * Kong.
 *
 * On success, the resolved [com.minimart.catalog.domain.model.CallerPrincipal]
 * is stashed as a request attribute for [CallerPrincipalArgumentResolver] to
 * inject into controller method parameters. On failure, this delegates to
 * the very [HandlerExceptionResolver] that backs `@RestControllerAdvice`, so
 * a missing/invalid/expired token produces the identical
 * `{"error":{"code":"UNAUTHORIZED",...}}` envelope
 * [com.minimart.catalog.web.GlobalExceptionHandler] defines for
 * [UnauthenticatedException].
 */
class JwtAuthenticationFilter(
    private val tokenVerifier: TokenVerifier,
    private val handlerExceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (request.method == HttpMethod.GET.name() || request.method == HttpMethod.OPTIONS.name()) {
            filterChain.doFilter(request, response)
            return
        }

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
        const val CALLER_PRINCIPAL_ATTRIBUTE = "com.minimart.catalog.CALLER_PRINCIPAL"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
