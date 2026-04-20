package com.gimlee.chat.persistence.model

import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.chat.domain.model.MessageType
import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import org.bson.types.ObjectId

data class ArchivedMessageDocument(
    val id: ObjectId,
    val chatId: String,
    val text: String?,
    val authorId: String,
    val author: String,
    val messageType: String,
    val systemCode: String?,
    val systemArgs: Map<String, String>?,
    val timestampMicros: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_CHAT_ID = "cid"
        const val FIELD_TEXT = "txt"
        const val FIELD_AUTHOR_ID = "aid"
        const val FIELD_AUTHOR = "auth"
        const val FIELD_MESSAGE_TYPE = "mt"
        const val FIELD_SYSTEM_CODE = "sc"
        const val FIELD_SYSTEM_ARGS = "sa"
        const val FIELD_TIMESTAMP = "ts"

        fun fromDomain(domain: ArchivedMessage): ArchivedMessageDocument = ArchivedMessageDocument(
            id = ObjectId(domain.id),
            chatId = domain.chatId,
            text = domain.text,
            authorId = domain.authorId,
            author = domain.author,
            messageType = domain.messageType.shortName,
            systemCode = domain.systemCode,
            systemArgs = domain.systemArgs,
            timestampMicros = domain.timestamp.toMicros()
        )
    }

    fun toDomain(): ArchivedMessage = ArchivedMessage(
        id = id.toHexString(),
        chatId = chatId,
        text = text,
        authorId = authorId,
        author = author,
        messageType = MessageType.fromShortName(messageType),
        systemCode = systemCode,
        systemArgs = systemArgs,
        timestamp = fromMicros(timestampMicros)
    )
}
