package com.gimlee.chat

import com.gimlee.auth.config.FilterConfig
import com.gimlee.auth.config.UtilConfig
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.chat.domain.model.ChatPrincipalProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.gimlee.chat", "com.gimlee.common.config"])
@Import(JwtTokenService::class, FilterConfig::class, UtilConfig::class)
class ChatTestApplication

fun main(args: Array<String>) {
    runApplication<ChatTestApplication>(*args)
}

@Configuration
class ChatTestConfig {

    @Bean
    fun chatPrincipalProvider(): ChatPrincipalProvider = object : ChatPrincipalProvider {
        override fun getUserId(): String = HttpServletRequestAuthUtil.getPrincipal().userId
        override fun getUsername(): String = HttpServletRequestAuthUtil.getPrincipal().username
    }
}
