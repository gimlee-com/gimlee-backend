package com.gimlee.chat.persistence.model

import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import org.bson.types.ObjectId

data class ArchivedMessageDocument(
    val id: ObjectId,
    val chatId: String,
    val text: String,
    val author: String,
    val timestampMicros: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_CHAT_ID = "cid"
        const val FIELD_TEXT = "txt"
        const val FIELD_AUTHOR = "auth"
        const val FIELD_TIMESTAMP = "ts"

        fun fromDomain(domain: ArchivedMessage): ArchivedMessageDocument = ArchivedMessageDocument(
            id = ObjectId(domain.id),
            chatId = domain.chatId,
            text = domain.text,
            author = domain.author,
            timestampMicros = domain.timestamp.toMicros()
        )
    }

    fun toDomain(): ArchivedMessage = ArchivedMessage(
        id = id.toHexString(),
        chatId = chatId,
        text = text,
        author = author,
        timestamp = fromMicros(timestampMicros)
    )
}
