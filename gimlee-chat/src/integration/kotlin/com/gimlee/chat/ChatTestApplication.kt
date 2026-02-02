package com.gimlee.chat

import com.gimlee.auth.config.FilterConfig
import com.gimlee.auth.config.UtilConfig
import com.gimlee.auth.service.JwtTokenService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.gimlee.chat", "com.gimlee.common.config"])
@Import(JwtTokenService::class, FilterConfig::class, UtilConfig::class)
class ChatTestApplication

fun main(args: Array<String>) {
    runApplication<ChatTestApplication>(*args)
}
