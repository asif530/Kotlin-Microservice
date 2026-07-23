package com.minimart.notification.web.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registers [CallerPrincipalArgumentResolver] with Spring MVC. Kept
 * separate from [JwtSecurityWebConfig] deliberately — see that class's
 * kdoc for the circular-dependency reason.
 */
@Configuration
class CallerPrincipalArgumentResolverConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CallerPrincipalArgumentResolver())
    }
}
