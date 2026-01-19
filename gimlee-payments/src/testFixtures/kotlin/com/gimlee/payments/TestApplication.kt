package com.gimlee.payments

import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.notifications.email.EmailService
import io.mockk.mockk
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication(scanBasePackages = ["com.gimlee.payments", "com.gimlee.common"])
class TestApplication

@Configuration
class MockConfig {
    @Bean
    fun userRoleRepository(): UserRoleRepository = mockk(relaxed = true)

    @Bean
    fun emailService(): EmailService = mockk(relaxed = true)
}

fun main(args: Array<String>) {
    runApplication<TestApplication>(*args)
}
