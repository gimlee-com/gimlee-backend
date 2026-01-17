package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.Category
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class CategoryRepository(mongoDatabase: MongoDatabase) {

    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-ads"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-categories"

        const val FIELD_ID = "_id"
        const val FIELD_SOURCE = "s"
        const val FIELD_SOURCE_TYPE = "s.type"
        const val FIELD_SOURCE_ID = "s.id"
        const val FIELD_PARENT = "p"
        const val FIELD_FLAGS = "f"
        const val FIELD_NAME = "n"
        const val FIELD_CREATED_AT = "crt"
        const val FIELD_UPDATED_AT = "upd"
        const val FIELD_DEPRECATED_FLAG = "dep" // Inside flags
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun getGptSourceIdToUuidMap(): Map<String, UUID> {
        val query = Filters.eq(FIELD_SOURCE_TYPE, Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY.shortName)
        val projection = Projections.include(FIELD_ID, FIELD_SOURCE_ID)

        return collection.find(query)
            .projection(projection)
            .map { doc ->
                val source = doc.get(FIELD_SOURCE, Document::class.java)
                val sourceId = source.getString("id")
                val id = doc.get(FIELD_ID, UUID::class.java)
                sourceId to id
            }
            .toList()
            .toMap()
    }

    fun upsertGptCategory(
        uuid: UUID,
        sourceId: String,
        parent: UUID?,
        nameMap: Map<String, Category.CategoryName>,
        now: Long
    ) {
        val query = Filters.and(
            Filters.eq(FIELD_SOURCE_TYPE, Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY.shortName),
            Filters.eq(FIELD_SOURCE_ID, sourceId)
        )

        val updates = Updates.combine(
            Updates.setOnInsert(FIELD_ID, uuid),
            Updates.setOnInsert(FIELD_CREATED_AT, now),
            Updates.setOnInsert(FIELD_SOURCE, Document("type", Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY.shortName).append("id", sourceId)),
            Updates.set(FIELD_PARENT, parent),
            Updates.set(FIELD_NAME, toNameDocument(nameMap)),
            Updates.set(FIELD_UPDATED_AT, now),
            Updates.set("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", false)
        )

        collection.updateOne(query, updates, UpdateOptions().upsert(true))
    }

    fun deprecateMissingGptCategories(beforeTimestamp: Long) {
        val query = Filters.and(
            Filters.eq(FIELD_SOURCE_TYPE, Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY.shortName),
            Filters.lt(FIELD_UPDATED_AT, beforeTimestamp),
            Filters.ne("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", true)
        )

        val update = Updates.set("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", true)

        collection.updateMany(query, update)
    }

    fun findAllGptCategories(): List<Category> {
        return collection.find(Filters.eq(FIELD_SOURCE_TYPE, Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY.shortName))
            .map { doc -> fromDocument(doc) }
            .toList()
    }

    private fun fromDocument(doc: Document): Category {
        val sourceDoc = doc.get(FIELD_SOURCE, Document::class.java)
        val nameDoc = doc.get(FIELD_NAME, Document::class.java)
        val flagsDoc = doc.get(FIELD_FLAGS, Document::class.java) ?: Document()

        val nameMap = nameDoc.mapValues { (_, value) ->
            val v = value as Document
            Category.CategoryName(v.getString("name"), v.getString("slug"))
        }

        val flagsMap = flagsDoc.mapValues { (_, value) -> value as Boolean }

        return Category(
            id = doc.get(FIELD_ID, UUID::class.java),
            source = Category.Source(
                type = Category.Source.Type.fromShortName(sourceDoc.getString("type")),
                id = sourceDoc.getString("id")
            ),
            parent = doc.get(FIELD_PARENT, UUID::class.java),
            flags = flagsMap,
            name = nameMap,
            createdAt = doc.getLong(FIELD_CREATED_AT),
            updatedAt = doc.getLong(FIELD_UPDATED_AT)
        )
    }

    private fun toNameDocument(nameMap: Map<String, Category.CategoryName>): Document {
        val doc = Document()
        nameMap.forEach { (lang, catName) ->
            doc.append(lang, Document("name", catName.name).append("slug", catName.slug))
        }
        return doc
    }
}

