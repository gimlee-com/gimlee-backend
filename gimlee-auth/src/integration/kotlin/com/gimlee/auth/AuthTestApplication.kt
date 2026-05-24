package com.gimlee.auth

import com.gimlee.notifications.email.EmailService
import io.mockk.mockk
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@SpringBootApplication(scanBasePackages = ["com.gimlee.auth", "com.gimlee.common.config"])
class AuthTestApplication

fun main(args: Array<String>) {
    runApplication<AuthTestApplication>(*args)
}

@Configuration
class AuthTestConfig {

    @Bean
    @Primary
    fun emailService(): EmailService = mockk(relaxed = true)
}
