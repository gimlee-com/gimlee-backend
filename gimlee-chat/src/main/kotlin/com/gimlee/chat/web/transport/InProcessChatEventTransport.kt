package com.gimlee.chat.web.transport

import com.gimlee.chat.domain.event.InternalChatEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * JVM-local [ChatEventTransport]. Events published on this instance are delivered only to listeners
 * registered on the same instance.
 *
 * This is the default transport and is correct for single-instance deployments. For multi-instance
 * deployments use [RedisChatEventTransport] (see docs/adr/0005-real-time-chat-horizontal-scaling.md).
 */
class InProcessChatEventTransport : ChatEventTransport {

    private val listeners = CopyOnWriteArrayList<ChatEventListener>()

    override fun publish(event: InternalChatEvent) {
        for (listener in listeners) {
            listener.onEvent(event)
        }
    }

    override fun subscribe(listener: ChatEventListener) {
        listeners.add(listener)
    }
}
