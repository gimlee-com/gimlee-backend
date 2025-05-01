package com.gimlee.auth.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.gimlee.auth.filter.JWTFilter
import com.gimlee.auth.util.JwtTokenVerifier

@Configuration
class FilterConfig(
    @Value("\${gimlee.auth.rest.unsecured-paths}")
    private val unsecuredPathPatterns: Array<String>
) {

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.auth", name = ["jwt.enabled"])
    fun jwtAuthFilterRegistration(
        @Qualifier("jwtTokenVerifier") jwtTokenVerifier: JwtTokenVerifier
    ): FilterRegistrationBean<*> {
        val registration = FilterRegistrationBean<JWTFilter>()
        registration.filter = JWTFilter(jwtTokenVerifier, unsecuredPathPatterns)
        registration.setName("jwtVerifyingFilter")
        registration.order = 2
        return registration
    }
}
