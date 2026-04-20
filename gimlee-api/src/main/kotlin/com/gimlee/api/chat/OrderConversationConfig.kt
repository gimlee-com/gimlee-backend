package com.gimlee.api.chat

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "gimlee.chat.order")
data class OrderConversationProperties(
    val lockOnComplete: Boolean = true,
    val lockOnFailed: Boolean = true,
    val systemMessagesEnabled: Boolean = true
)

@Configuration
@EnableConfigurationProperties(OrderConversationProperties::class)
class OrderConversationConfig
