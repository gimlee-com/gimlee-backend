package com.gimlee.chat.web

import com.gimlee.chat.domain.event.InternalChatEvent
import com.gimlee.chat.domain.model.MessageType
import com.gimlee.chat.web.dto.response.LocalizedChatEventDto
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Broadcaster that manages SSE emitters and buffers events for efficient real-time delivery.
 * 
 * Emitters are tracked by (chatId, userId) for access control.
 * 
 * NOTE: This implementation is JVM-local. For horizontal scaling, "Sticky Chat" load balancing 
 * (pinning chatId to instances) or a distributed event bus (e.g. Redis) is required.
 * See gimlee-chat/docs/architecture.md for details.
 */
@Component
class ChatEventBroadcaster(
    private val messageSource: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class UserEmitter(val userId: String, val emitter: SseEmitter, val locale: Locale)

    // chatId -> List of user emitters
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<UserEmitter>>()

    // chatId -> Buffer of events
    private val eventBuffer = ConcurrentHashMap<String, MutableList<InternalChatEvent>>()

    fun createEmitter(chatId: String, userId: String? = null, locale: Locale = Locale.ENGLISH): SseEmitter {
        val emitter = SseEmitter(0L) // Infinite timeout
        val userEmitter = UserEmitter(userId = userId ?: "", emitter = emitter, locale = locale)
        val chatEmitters = emitters.computeIfAbsent(chatId) { CopyOnWriteArrayList() }
        chatEmitters.add(userEmitter)

        emitter.onCompletion { chatEmitters.remove(userEmitter) }
        emitter.onTimeout { chatEmitters.remove(userEmitter) }
        emitter.onError { chatEmitters.remove(userEmitter) }

        return emitter
    }

    fun closeEmittersForConversation(chatId: String) {
        val chatEmitters = emitters.remove(chatId) ?: return
        chatEmitters.forEach { userEmitter ->
            try {
                userEmitter.emitter.complete()
            } catch (_: Exception) {
                // Already closed
            }
        }
    }

    fun closeEmitterForUser(chatId: String, userId: String) {
        val chatEmitters = emitters[chatId] ?: return
        val toRemove = chatEmitters.filter { it.userId == userId }
        toRemove.forEach { userEmitter ->
            try {
                userEmitter.emitter.complete()
            } catch (_: Exception) {
                // Already closed
            }
            chatEmitters.remove(userEmitter)
        }
    }

    @EventListener
    fun handleInternalChatEvent(event: InternalChatEvent) {
        if (!emitters.containsKey(event.chatId)) return
        
        eventBuffer.computeIfAbsent(event.chatId) { CopyOnWriteArrayList() }.add(event)
    }

    @Scheduled(fixedRateString = "\${gimlee.chat.sse.buffer-ms:200}")
    fun flushBuffers() {
        if (eventBuffer.isEmpty()) return

        val keys = eventBuffer.keys().toList()
        for (chatId in keys) {
            val events = eventBuffer.remove(chatId) ?: continue
            if (events.isEmpty()) continue

            val chatEmitters = emitters[chatId] ?: continue
            
            val deadEmitters = mutableListOf<UserEmitter>()
            for (userEmitter in chatEmitters) {
                try {
                    val localizedEvents = events.map { event ->
                        val data = if (event.messageType == MessageType.SYSTEM && event.systemCode != null) {
                            val key = "gimlee.chat.system.${event.systemCode}"
                            val status = event.systemArgs?.get("status")
                            val localizedKey = if (status != null) "$key.$status" else key
                            messageSource.getMessage(localizedKey, event.systemArgs?.values?.toTypedArray(), userEmitter.locale)
                        } else {
                            event.data
                        }

                        LocalizedChatEventDto(
                            chatId = event.chatId,
                            type = event.type,
                            data = data,
                            authorId = event.authorId,
                            author = event.author,
                            messageType = event.messageType,
                            timestamp = event.timestamp
                        )
                    }

                    userEmitter.emitter.send(
                        SseEmitter.event()
                            .name("chat-event")
                            .data(localizedEvents)
                    )
                } catch (e: IOException) {
                    deadEmitters.add(userEmitter)
                }
            }
            
            if (deadEmitters.isNotEmpty()) {
                chatEmitters.removeAll(deadEmitters)
            }
        }
    }

    @Scheduled(fixedRateString = "\${gimlee.chat.sse.heartbeat-ms:30000}")
    fun sendHeartbeats() {
        emitters.values.forEach { chatEmitters ->
            val deadEmitters = mutableListOf<UserEmitter>()
            chatEmitters.forEach { userEmitter ->
                try {
                    userEmitter.emitter.send(
                        SseEmitter.event()
                            .name("heartbeat")
                            .comment("keep-alive")
                    )
                } catch (e: IOException) {
                    deadEmitters.add(userEmitter)
                }
            }
            chatEmitters.removeAll(deadEmitters)
        }
    }

    @PreDestroy
    fun onShutdown() {
        log.info("Completing all chat SSE emitters and flushing buffers before shutdown...")
        flushBuffers()
        emitters.values.forEach { chatEmitters ->
            chatEmitters.forEach { userEmitter ->
                try {
                    userEmitter.emitter.complete()
                } catch (_: Exception) {
                    // Already closed
                }
            }
        }
        emitters.clear()
    }
}
