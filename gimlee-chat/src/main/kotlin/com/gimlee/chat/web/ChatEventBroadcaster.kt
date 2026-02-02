package com.gimlee.chat.web

import com.gimlee.events.InternalChatEvent
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Broadcaster that manages SSE emitters and buffers events for efficient real-time delivery.
 * 
 * NOTE: This implementation is JVM-local. For horizontal scaling, "Sticky Chat" load balancing 
 * (pinning chatId to instances) or a distributed event bus (e.g. Redis) is required.
 * See gimlee-chat/docs/architecture.md for details.
 */
@Component
class ChatEventBroadcaster {
    private val log = LoggerFactory.getLogger(javaClass)
    
    // chatId -> List of emitters
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    // chatId -> Buffer of events
    private val eventBuffer = ConcurrentHashMap<String, MutableList<InternalChatEvent>>()

    fun createEmitter(chatId: String): SseEmitter {
        val emitter = SseEmitter(0L) // Infinite timeout
        val chatEmitters = emitters.computeIfAbsent(chatId) { CopyOnWriteArrayList() }
        chatEmitters.add(emitter)

        emitter.onCompletion { chatEmitters.remove(emitter) }
        emitter.onTimeout { chatEmitters.remove(emitter) }
        emitter.onError { chatEmitters.remove(emitter) }

        return emitter
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
            
            val deadEmitters = mutableListOf<SseEmitter>()
            for (emitter in chatEmitters) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("chat-event")
                            .data(events)
                    )
                } catch (e: IOException) {
                    deadEmitters.add(emitter)
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
            val deadEmitters = mutableListOf<SseEmitter>()
            chatEmitters.forEach { emitter ->
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("heartbeat")
                            .comment("keep-alive")
                    )
                } catch (e: IOException) {
                    deadEmitters.add(emitter)
                }
            }
            chatEmitters.removeAll(deadEmitters)
        }
    }

    @PreDestroy
    fun onShutdown() {
        log.info("Flushing remaining chat events to emitters before shutdown...")
        flushBuffers()
    }
}
