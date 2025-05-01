package com.gimlee.mediastore.persistence

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.ReturnDocument
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.mediastore.domain.MediaItem
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_EXTENSION
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_FILENAME
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_ID
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_MD_THUMB_PATH
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_PATH
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_SM_THUMB_PATH
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_TIMESTAMP
import com.gimlee.mediastore.domain.MediaItem.Companion.FIELD_XS_THUMB_PATH

@Repository
class MediaItemRepository(
    private val mongoDatabase: MongoDatabase
) {

    companion object {
        const val MEDIA_ITEMS_COLLECTION_NAME = "gimlee-media-items"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(MEDIA_ITEMS_COLLECTION_NAME)
    }

    fun findByName(fileName: String): MediaItem? {
        val query = Filters.eq(FIELD_FILENAME, fileName)
        return collection.find(query)
            .limit(1)
            .firstOrNull()?.toMediaItem()
    }

    fun findByIds(ids: List<String>): List<MediaItem> {
        val objectIds = ids.mapNotNull {
            try {
                ObjectId(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        if (objectIds.isEmpty() && ids.isNotEmpty()) {
            return emptyList()
        }
        if (objectIds.isEmpty()) {
            return emptyList()
        }
        val query = Filters.`in`(FIELD_ID, objectIds)
        return collection.find(query)
            .map { it.toMediaItem() }
            .toList()
    }

    fun findAll(): List<MediaItem> {
        return collection.find()
            .map { it.toMediaItem() }
            .toList()
    }

    /**
     * Saves (inserts or replaces) a MediaItem.
     * If mediaItem.id is null, a new ObjectId is generated and an insert is performed.
     * If mediaItem.id is not null, it attempts to replace the document with that _id (upsert).
     */
    fun save(mediaItem: MediaItem): MediaItem {
        val doc = mediaItem.toDocument()
        val objectIdToUse: ObjectId

        if (mediaItem.id == null) {
            objectIdToUse = ObjectId()
            doc[FIELD_ID] = objectIdToUse
            collection.insertOne(doc)
        } else {
            try {
                objectIdToUse = ObjectId(mediaItem.id)
                doc[FIELD_ID] = objectIdToUse
                val filter = Filters.eq(FIELD_ID, objectIdToUse)
                val options = FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)

                collection.findOneAndReplace(filter, doc, options)

            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid ObjectId format provided in MediaItem.id: ${mediaItem.id}", e)
            }
        }

        return mediaItem.copy(id = objectIdToUse.toHexString())
    }

    private fun Document.toMediaItem() = MediaItem(
            id = this.getObjectId(FIELD_ID).toHexString(),
            filename = this.getString(FIELD_FILENAME),
            extension = this.getString(FIELD_EXTENSION),
            dateTime = fromMicros(this.getLong(FIELD_TIMESTAMP)),
            path = this.getString(FIELD_PATH),
            xsThumbPath = this.getString(FIELD_XS_THUMB_PATH),
            smThumbPath = this.getString(FIELD_SM_THUMB_PATH),
            mdThumbPath = this.getString(FIELD_MD_THUMB_PATH)
        )

    private fun MediaItem.toDocument(): Document {
        val doc = Document()
            .append(FIELD_FILENAME, filename)
            .append(FIELD_EXTENSION, extension)
            .append(FIELD_TIMESTAMP, dateTime.toMicros())
            .append(FIELD_PATH, path)
            .append(FIELD_XS_THUMB_PATH, xsThumbPath)
            .append(FIELD_SM_THUMB_PATH, smThumbPath)
            .append(FIELD_MD_THUMB_PATH, mdThumbPath)

        xsThumbPath?.let { doc.append(FIELD_XS_THUMB_PATH, it) }
        smThumbPath?.let { doc.append(FIELD_SM_THUMB_PATH, it) }
        mdThumbPath?.let { doc.append(FIELD_MD_THUMB_PATH, it) }

        return doc
    }

}