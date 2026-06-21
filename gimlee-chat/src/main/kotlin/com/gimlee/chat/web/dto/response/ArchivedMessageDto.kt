package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.chat.domain.model.MessageType
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.context.MessageSource
import java.time.Instant
import java.util.*

@Schema(description = "Information about an archived chat message")
data class ArchivedMessageDto(
    @field:Schema(description = "Message ID")
    val id: String,
    @field:Schema(description = "Chat ID")
    val chatId: String,
    @field:Schema(description = "Message text (localized for system messages)")
    val text: String?,
    @field:Schema(description = "Author ID (empty for system messages)")
    val authorId: String,
    @field:Schema(description = "Author name")
    val author: String,
    @field:Schema(description = "Message type (REGULAR, SYSTEM)")
    val messageType: MessageType,
    @field:Schema(description = "When the message was sent")
    val timestamp: Instant
) {
    companion object {
        fun from(message: ArchivedMessage, messageSource: MessageSource, locale: Locale): ArchivedMessageDto {
            val text = if (message.messageType == MessageType.SYSTEM && message.systemCode != null) {
                val key = "gimlee.chat.system.${message.systemCode}"
                val status = message.systemArgs?.get("status")
                val localizedKey = if (status != null) "$key.$status" else key
                messageSource.getMessage(localizedKey, message.systemArgs?.values?.toTypedArray(), locale)
            } else {
                message.text
            }

            return ArchivedMessageDto(
                id = message.id,
                chatId = message.chatId,
                text = text,
                authorId = message.authorId,
                author = message.author,
                messageType = message.messageType,
                timestamp = message.timestamp
            )
        }
    }
}
