package com.gimlee.chat.domain

import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.chat.domain.model.ChatEventType
import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.events.ChatMessageSentEvent
import com.gimlee.events.InternalChatEvent
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    /**
     * Sends a message to a chat.
     * Persists the message immediately and broadcasts it internally.
     */
    fun sendMessage(chatId: String, author: String, text: String): ArchivedMessage {
        val message = ArchivedMessage(
            id = ObjectId.get().toHexString(),
            chatId = chatId,
            author = author,
            text = text,
            timestamp = Instant.now()
        )
        chatRepository.saveMessage(message)
        
        // Broadcast for real-time subscribers (local instance)
        // Scaling note: Requires "Sticky Chat" routing or distributed bus.
        eventPublisher.publishEvent(InternalChatEvent(
            chatId = chatId,
            type = ChatEventType.MESSAGE.name,
            data = text,
            author = author,
            timestamp = message.timestamp
        ))
        
        // Notify other modules that a message was sent
        eventPublisher.publishEvent(ChatMessageSentEvent(
            chatId = chatId,
            author = author,
            text = text,
            timestamp = message.timestamp
        ))
        
        return message
    }

    /**
     * Broadcasts a typing indicator.
     */
    fun indicateTyping(chatId: String, author: String) {
        eventPublisher.publishEvent(InternalChatEvent(
            chatId = chatId,
            type = ChatEventType.TYPING_INDICATOR.name,
            author = author
        ))
    }

    /**
     * Gets archived messages for a chat.
     */
    fun getHistory(chatId: String, limit: Int, beforeId: String?): List<ArchivedMessage> {
        return chatRepository.findMessages(chatId, limit, beforeId)
    }
}
