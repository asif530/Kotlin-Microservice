package com.minimart.notification.web.security

import com.minimart.notification.domain.model.CallerPrincipal
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Injects the [CallerPrincipal] [JwtAuthenticationFilter] resolved and
 * stashed as a request attribute into any controller method parameter of
 * type [CallerPrincipal] — mirrors identity-service/catalog-service/
 * order-service's own CallerPrincipalArgumentResolver.
 */
class CallerPrincipalArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == CallerPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val attribute = webRequest.getAttribute(
            JwtAuthenticationFilter.CALLER_PRINCIPAL_ATTRIBUTE,
            NativeWebRequest.SCOPE_REQUEST,
        )
        return attribute as? CallerPrincipal
            ?: error(
                "No CallerPrincipal request attribute found for parameter " +
                    "'${parameter.parameterName}' on ${parameter.method}. Is this controller " +
                    "method's route covered by JwtSecurityWebConfig's filter registration " +
                    "(/api/notifications)?",
            )
    }
}
