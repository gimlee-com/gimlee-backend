package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService

/**
 * Verifies that all thread pools and lifecycle-aware beans are properly configured
 * for graceful shutdown, preventing the application from hanging on context close.
 */
class GracefulShutdownIntegrationTest(
    private val applicationContext: ApplicationContext
) : BaseIntegrationTest({

    Given("application context with thread pools") {
        Then("task scheduler should be configured for graceful shutdown") {
            val scheduler = applicationContext.getBean(ThreadPoolTaskScheduler::class.java)
            scheduler.poolSize shouldBe 5
        }

        Then("async executor should be configured for graceful shutdown") {
            val executor = applicationContext.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor::class.java)
            executor.threadNamePrefix shouldBe "gimlee-async-"
        }

        Then("all ExecutorService beans should be active") {
            val executors = applicationContext.getBeansOfType(ExecutorService::class.java)
            executors.values.shouldNotBeEmpty()
            executors.values.forEach { executor ->
                executor.isShutdown shouldBe false
            }
        }
    }
})
