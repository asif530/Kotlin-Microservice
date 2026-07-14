package com.minimart.identity.web.security

import com.minimart.identity.domain.exception.UnauthenticatedException
import com.minimart.identity.domain.port.TokenVerifier
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Servlet-level gate for every request under /api/users — scoped to that path
 * exclusively via [JwtSecurityWebConfig]'s `FilterRegistrationBean`, not by
 * URL matching in here.
 *
 * identity-service deliberately does not run the full
 * spring-boot-starter-security filter chain (see `PasswordEncoderConfig`'s
 * kdoc: Phase 1 kept that footprint minimal because Kong owned all
 * authentication at the gateway). Phase 2 changes what's needed, not that
 * decision: Kong's jwt plugin only verifies signature and expiry
 * (kong.decl.yaml's `claims_to_verify: [exp]`), never role-based
 * authorization, and the only Kong consumer configured is
 * identity-service's own token-signing identity — there is no consumer
 * whose forwarded `X-Consumer-*` headers could stand in for the real end
 * user, and Kong forwards the original `Authorization` header through
 * untouched. identity-service therefore has to independently verify the
 * token and resolve the caller's identity itself for its own admin-only
 * endpoints. A single lightweight `OncePerRequestFilter` (plain spring-web,
 * no new dependency) is the narrowest way to do that while still fulfilling
 * the task brief's "wire this into Spring Security or an equivalent
 * filter" — see the Phase-2 implementation summary for why the full
 * spring-security starter was not added instead.
 *
 * On success, the resolved [com.minimart.identity.domain.model.CallerPrincipal]
 * is stashed as a request attribute for [CallerPrincipalArgumentResolver] to
 * inject into controller method parameters. On failure, this delegates to
 * the very [HandlerExceptionResolver] that backs `@RestControllerAdvice`, so
 * a missing/invalid/expired token produces the identical
 * `{"error":{"code":"UNAUTHORIZED",...}}` envelope
 * [com.minimart.identity.web.GlobalExceptionHandler] defines for
 * [UnauthenticatedException] — filters run outside `DispatcherServlet`'s
 * normal exception handling, so reusing its resolver explicitly is the
 * standard way to keep the error envelope consistent.
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
        const val CALLER_PRINCIPAL_ATTRIBUTE = "com.minimart.identity.CALLER_PRINCIPAL"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
