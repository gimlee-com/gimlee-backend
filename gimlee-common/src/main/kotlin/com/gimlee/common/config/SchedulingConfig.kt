package com.gimlee.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.beans.factory.annotation.Value

@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean
    fun taskScheduler(@Value("\${gimlee.scheduling.pool-size:5}") poolSize: Int): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize
        scheduler.setThreadNamePrefix("gimlee-scheduler-")
        scheduler.initialize()
        return scheduler
    }
}
