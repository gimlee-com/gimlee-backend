package com.gimlee.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.chat.web.transport.ChatEventTransport
import com.gimlee.chat.web.transport.InProcessChatEventTransport
import com.gimlee.chat.web.transport.RedisChatEventTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@ConfigurationProperties(prefix = "gimlee.chat")
data class ChatProperties(
    val lockSweepIntervalMs: Long = 60000,
    val lockSweepBatchSize: Int = 100,
    val sse: SseProperties = SseProperties()
)

data class SseProperties(
    /** Cross-instance event transport: "in-process" (single node) or "redis" (horizontal scaling). */
    val transport: String = "in-process",
    val bufferMs: Long = 200,
    val heartbeatMs: Long = 30000,
    val redis: SseRedisProperties = SseRedisProperties()
)

data class SseRedisProperties(
    /** Redis Pub/Sub channel used to fan chat events out across instances. */
    val channel: String = "gimlee.chat.events",
    val poolSize: Int = 8
)

@Configuration
@EnableConfigurationProperties(ChatProperties::class)
class ChatConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.chat.sse", name = ["transport"], havingValue = "in-process", matchIfMissing = true)
    fun inProcessChatEventTransport(): ChatEventTransport = InProcessChatEventTransport()

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.chat.sse", name = ["transport"], havingValue = "redis")
    fun redisChatEventTransport(
        redisTemplate: StringRedisTemplate,
        objectMapper: ObjectMapper,
        properties: ChatProperties
    ): ChatEventTransport = RedisChatEventTransport(redisTemplate, objectMapper, properties.sse.redis.channel)

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.chat.sse", name = ["transport"], havingValue = "redis")
    fun redisChatListenerExecutor(
        @Value("\${gimlee.chat.sse.redis.pool-size:8}") poolSize: Int
    ): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = poolSize
        executor.maxPoolSize = poolSize
        executor.setQueueCapacity(500)
        executor.setThreadNamePrefix("redis-chat-listener-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(10)
        executor.initialize()
        return executor
    }

    @Bean
    @ConditionalOnProperty(prefix = "gimlee.chat.sse", name = ["transport"], havingValue = "redis")
    fun chatRedisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        chatEventTransport: ChatEventTransport,
        redisChatListenerExecutor: ThreadPoolTaskExecutor,
        properties: ChatProperties
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.setTaskExecutor(redisChatListenerExecutor)
        container.setSubscriptionExecutor(redisChatListenerExecutor)
        container.addMessageListener(
            chatEventTransport as RedisChatEventTransport,
            ChannelTopic(properties.sse.redis.channel)
        )
        return container
    }
}
