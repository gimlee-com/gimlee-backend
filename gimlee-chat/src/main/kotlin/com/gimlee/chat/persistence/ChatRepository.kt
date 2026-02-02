package com.gimlee.chat.persistence

import com.gimlee.chat.domain.model.ArchivedMessage
import com.gimlee.chat.persistence.model.ArchivedMessageDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class ChatRepository(mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-chat-messages"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun saveMessage(message: ArchivedMessage) {
        val document = mapToDocument(ArchivedMessageDocument.fromDomain(message))
        collection.insertOne(document)
    }

    fun saveMessages(messages: List<ArchivedMessage>) {
        if (messages.isEmpty()) return
        val documents = messages.map { mapToDocument(ArchivedMessageDocument.fromDomain(it)) }
        collection.insertMany(documents)
    }

    fun findMessages(chatId: String, limit: Int, beforeId: String? = null): List<ArchivedMessage> {
        val queryFilters = mutableListOf<Bson>()
        queryFilters.add(Filters.eq(ArchivedMessageDocument.FIELD_CHAT_ID, chatId))

        beforeId?.let {
            queryFilters.add(Filters.lt(ArchivedMessageDocument.FIELD_ID, ObjectId(it)))
        }

        val filter = Filters.and(queryFilters)

        return collection.find(filter)
            .sort(Sorts.descending(ArchivedMessageDocument.FIELD_ID))
            .limit(limit)
            .map { mapToArchivedMessageDocument(it).toDomain() }
            .toList()
    }

    fun clear() {
        collection.deleteMany(Document())
    }

    private fun mapToDocument(doc: ArchivedMessageDocument): Document {
        return Document()
            .append(ArchivedMessageDocument.FIELD_ID, doc.id)
            .append(ArchivedMessageDocument.FIELD_CHAT_ID, doc.chatId)
            .append(ArchivedMessageDocument.FIELD_TEXT, doc.text)
            .append(ArchivedMessageDocument.FIELD_AUTHOR, doc.author)
            .append(ArchivedMessageDocument.FIELD_TIMESTAMP, doc.timestampMicros)
    }

    private fun mapToArchivedMessageDocument(doc: Document): ArchivedMessageDocument {
        return ArchivedMessageDocument(
            id = doc.getObjectId(ArchivedMessageDocument.FIELD_ID),
            chatId = doc.getString(ArchivedMessageDocument.FIELD_CHAT_ID),
            text = doc.getString(ArchivedMessageDocument.FIELD_TEXT),
            author = doc.getString(ArchivedMessageDocument.FIELD_AUTHOR),
            timestampMicros = doc.getLong(ArchivedMessageDocument.FIELD_TIMESTAMP)
        )
    }
}
