package com.gimlee.chat

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "gimlee.chat")
data class ChatProperties(
    val lockSweepIntervalMs: Long = 60000,
    val lockSweepBatchSize: Int = 100
)

@Configuration
@EnableConfigurationProperties(ChatProperties::class)
class ChatConfig
