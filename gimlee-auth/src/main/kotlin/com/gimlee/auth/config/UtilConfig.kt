package com.gimlee.auth.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.gimlee.auth.util.JwtTokenVerifier

@Configuration
@EnableConfigurationProperties(BanProperties::class)
class UtilConfig(
    @Value("\${gimlee.auth.rest.jwt.key:}")
    private val jwtKey: String?,

    @Value("\${gimlee.auth.rest.jwt.issuer:}")
    private val jwtIssuer: String?
) {

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.auth", name = ["jwt.enabled"])
    fun jwtTokenVerifier() = JwtTokenVerifier(jwtKey, jwtIssuer)
}
