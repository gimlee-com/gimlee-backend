package com.gimlee.chat.domain

import com.gimlee.chat.domain.event.InternalChatEvent
import com.gimlee.chat.domain.event.MessageSentEvent
import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.chat.domain.model.ChatEventType
import com.gimlee.chat.domain.model.MessageType
import com.gimlee.chat.persistence.ChatRepository
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun sendMessage(chatId: String, authorId: String, authorName: String, text: String): ArchivedMessage {
        val message = ArchivedMessage(
            id = ObjectId.get().toHexString(),
            chatId = chatId,
            authorId = authorId,
            author = authorName,
            text = text,
            messageType = MessageType.REGULAR,
            timestamp = Instant.now()
        )
        chatRepository.saveMessage(message)

        eventPublisher.publishEvent(InternalChatEvent(
            chatId = chatId,
            type = ChatEventType.MESSAGE.name,
            data = text,
            authorId = authorId,
            author = authorName,
            timestamp = message.timestamp
        ))

        eventPublisher.publishEvent(MessageSentEvent(
            conversationId = chatId,
            messageId = message.id,
            authorId = authorId,
            authorName = authorName,
            text = text,
            timestamp = message.timestamp
        ))

        return message
    }

    fun sendSystemMessage(chatId: String, systemCode: String, systemArgs: Map<String, String>): ArchivedMessage {
        val message = ArchivedMessage(
            id = ObjectId.get().toHexString(),
            chatId = chatId,
            authorId = "",
            author = "system",
            text = null,
            messageType = MessageType.SYSTEM,
            systemCode = systemCode,
            systemArgs = systemArgs,
            timestamp = Instant.now()
        )
        chatRepository.saveMessage(message)

        eventPublisher.publishEvent(InternalChatEvent(
            chatId = chatId,
            type = ChatEventType.MESSAGE.name,
            data = systemCode,
            author = "system",
            timestamp = message.timestamp
        ))

        return message
    }

    fun indicateTyping(chatId: String, authorId: String, authorName: String) {
        eventPublisher.publishEvent(InternalChatEvent(
            chatId = chatId,
            type = ChatEventType.TYPING_INDICATOR.name,
            authorId = authorId,
            author = authorName
        ))
    }

    fun getHistory(chatId: String, limit: Int, beforeId: String?): List<ArchivedMessage> {
        return chatRepository.findMessages(chatId, limit, beforeId)
    }
}
