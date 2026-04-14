package com.gimlee.api.notifications

import com.gimlee.notifications.email.EmailService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor

@TestConfiguration
class NotificationTestConfig {

    @Bean
    @Primary
    fun syncTaskExecutor(): TaskExecutor = SyncTaskExecutor()

    @Bean
    @Primary
    fun testEmailService(): EmailService = mockk(relaxed = true)
}
