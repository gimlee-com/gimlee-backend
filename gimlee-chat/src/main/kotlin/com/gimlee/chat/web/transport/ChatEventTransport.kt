package com.gimlee.chat.web.transport

import com.gimlee.chat.domain.event.InternalChatEvent

/**
 * Abstraction over the cross-instance delivery of real-time chat events.
 *
 * Implementations fan an [InternalChatEvent] out to every application instance that has active SSE
 * subscribers for the event's chat. The default in-process implementation keeps delivery local to a
 * single JVM; the Redis implementation broadcasts through a shared pub/sub channel so any
 * participant can connect to any instance.
 *
 * See docs/adr/0005-real-time-chat-horizontal-scaling.md.
 */
interface ChatEventTransport {

    /**
     * Publishes an event to all application instances (including the publishing one). Local
     * delivery happens via the registered [ChatEventListener]s.
     */
    fun publish(event: InternalChatEvent)

    /**
     * Registers a listener that receives every event delivered to this instance, regardless of the
     * instance on which the event originated.
     */
    fun subscribe(listener: ChatEventListener)
}

/**
 * Handles a chat event delivered to the local instance by a [ChatEventTransport].
 */
fun interface ChatEventListener {
    fun onEvent(event: InternalChatEvent)
}
