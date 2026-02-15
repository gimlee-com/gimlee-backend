package com.gimlee.user

import com.gimlee.auth.service.UserService
import com.gimlee.auth.user.UserVerificationService
import com.gimlee.notifications.email.EmailService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class UserTestConfig {
    @Bean
    fun emailService(): EmailService = mockk(relaxed = true)

    @Bean
    fun userService(): UserService = mockk(relaxed = true)

    @Bean
    fun userVerificationService(): UserVerificationService = mockk(relaxed = true)
}
