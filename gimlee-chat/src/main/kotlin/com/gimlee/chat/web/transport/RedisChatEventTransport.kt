package com.gimlee.chat.web.transport

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.chat.domain.event.InternalChatEvent
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Distributed [ChatEventTransport] backed by Redis Pub/Sub.
 *
 * Publishing serializes the event to JSON and sends it to a shared channel. Every instance
 * subscribed to that channel (including the publishing one) receives the event via [onMessage] and
 * dispatches it to its locally registered [ChatEventListener]s. This decouples real-time delivery
 * from load-balancer affinity: any participant can connect to any instance.
 *
 * Delivery is fire-and-forget — Redis Pub/Sub does not persist or replay. MongoDB remains the source
 * of truth for chat history; clients back-fill missed messages on reconnect via cursor pagination.
 *
 * See docs/adr/0005-real-time-chat-horizontal-scaling.md.
 */
class RedisChatEventTransport(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val channel: String
) : ChatEventTransport, MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)
    private val listeners = CopyOnWriteArrayList<ChatEventListener>()

    override fun publish(event: InternalChatEvent) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event))
        } catch (e: Exception) {
            log.error("Failed to publish chat event for chat {} to Redis channel {}", event.chatId, channel, e)
        }
    }

    override fun subscribe(listener: ChatEventListener) {
        listeners.add(listener)
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = try {
            objectMapper.readValue(message.body, InternalChatEvent::class.java)
        } catch (e: Exception) {
            log.error("Failed to deserialize chat event from Redis channel {}", channel, e)
            return
        }
        for (listener in listeners) {
            listener.onEvent(event)
        }
    }
}
