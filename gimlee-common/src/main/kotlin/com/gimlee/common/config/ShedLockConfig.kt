package com.gimlee.common.config

import com.mongodb.client.MongoDatabase
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class ShedLockConfig {

    @Bean
    fun lockProvider(mongoDatabase: MongoDatabase): LockProvider {
        return MongoLockProvider(mongoDatabase.getCollection("gimlee-shedlock"))
    }
}
