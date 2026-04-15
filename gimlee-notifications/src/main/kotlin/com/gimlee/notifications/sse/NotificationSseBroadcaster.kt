package com.gimlee.notifications.sse

import com.gimlee.notifications.web.dto.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import jakarta.annotation.PreDestroy

/**
 * Manages per-user SSE emitters for real-time notification delivery.
 *
 * NOTE: This implementation is JVM-local. For horizontal scaling, a distributed
 * event bus (e.g., Redis Pub/Sub) is required to fan out events across instances.
 */
@Component
class NotificationSseBroadcaster(
    @Value("\${gimlee.notifications.sse.timeout-ms:0}")
    private val sseTimeoutMs: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun createEmitter(userId: String): SseEmitter {
        val emitter = SseEmitter(sseTimeoutMs)
        val userEmitters = emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }
        userEmitters.add(emitter)

        val cleanup: () -> Unit = { userEmitters.remove(emitter) }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup() }

        return emitter
    }

    fun broadcast(userId: String, event: NotificationSseEvent) {
        val userEmitters = emitters[userId] ?: return
        if (userEmitters.isEmpty()) return

        val (eventName, data) = resolveEventPayload(event)
        val deadEmitters = mutableListOf<SseEmitter>()

        for (emitter in userEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(data)
                )
            } catch (e: IOException) {
                deadEmitters.add(emitter)
            }
        }

        if (deadEmitters.isNotEmpty()) {
            userEmitters.removeAll(deadEmitters.toSet())
        }
    }

    fun hasActiveEmitters(userId: String): Boolean {
        val userEmitters = emitters[userId] ?: return false
        return userEmitters.isNotEmpty()
    }

    @Scheduled(fixedRateString = "\${gimlee.notifications.sse.heartbeat-ms:30000}")
    fun sendHeartbeats() {
        emitters.forEach { (_, userEmitters) ->
            val deadEmitters = mutableListOf<SseEmitter>()
            for (emitter in userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"))
                } catch (e: IOException) {
                    deadEmitters.add(emitter)
                }
            }
            if (deadEmitters.isNotEmpty()) {
                userEmitters.removeAll(deadEmitters.toSet())
            }
        }
    }

    @PreDestroy
    fun onShutdown() {
        log.info("Completing all notification SSE emitters on shutdown")
        emitters.values.forEach { userEmitters ->
            userEmitters.forEach { emitter ->
                try {
                    emitter.complete()
                } catch (_: Exception) { }
            }
        }
        emitters.clear()
    }

    private fun resolveEventPayload(event: NotificationSseEvent): Pair<String, Any> = when (event) {
        is NotificationSseEvent.Created -> "notification" to NotificationDto.from(event.notification)
        is NotificationSseEvent.Read -> "notification-read" to mapOf("id" to event.notificationId)
        is NotificationSseEvent.AllRead -> "notifications-all-read" to mapOf("category" to event.category)
        is NotificationSseEvent.UnreadCountChanged -> "unread-count" to UnreadCountDataDto(event.count)
    }
}
