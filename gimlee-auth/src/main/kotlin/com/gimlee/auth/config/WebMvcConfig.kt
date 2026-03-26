package com.gimlee.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.interceptor.BannedUserInterceptor
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val bannedUserCache: BannedUserCache,
    private val messageSource: MessageSource,
    private val objectMapper: ObjectMapper
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(BannedUserInterceptor(bannedUserCache, messageSource, objectMapper))
    }
}
