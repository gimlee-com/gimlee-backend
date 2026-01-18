package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.Category
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.*
import org.bson.Document
import org.springframework.stereotype.Repository

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

    fun getSourceIdToIdMapBySourceType(sourceType: Category.Source.Type): Map<String, Int> {
        val query = Filters.eq(FIELD_SOURCE_TYPE, sourceType.shortName)
        val projection = Projections.include(FIELD_ID, FIELD_SOURCE_ID)

        return collection.find(query)
            .projection(projection)
            .map { doc ->
                val source = doc.get(FIELD_SOURCE, Document::class.java)
                val sourceId = source.getString("id")
                val id = doc.getInteger(FIELD_ID)
                sourceId to id
            }
            .toList()
            .toMap()
    }

    fun upsertCategoryBySourceType(
        sourceType: Category.Source.Type,
        id: Int,
        sourceId: String,
        parent: Int?,
        nameMap: Map<String, Category.CategoryName>,
        now: Long
    ) {
        val query = Filters.and(
            Filters.eq(FIELD_SOURCE_TYPE, sourceType.shortName),
            Filters.eq(FIELD_SOURCE_ID, sourceId)
        )

        val updates = Updates.combine(
            Updates.setOnInsert(FIELD_ID, id),
            Updates.setOnInsert(FIELD_CREATED_AT, now),
            Updates.setOnInsert(FIELD_SOURCE, Document("type", sourceType.shortName).append("id", sourceId)),
            Updates.set(FIELD_PARENT, parent),
            Updates.set(FIELD_NAME, toNameDocument(nameMap)),
            Updates.set(FIELD_UPDATED_AT, now),
            Updates.set("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", false)
        )

        collection.updateOne(query, updates, UpdateOptions().upsert(true))
    }

    fun deprecateMissingCategoriesBySourceType(sourceType: Category.Source.Type, beforeTimestamp: Long) {
        val query = Filters.and(
            Filters.eq(FIELD_SOURCE_TYPE, sourceType.shortName),
            Filters.lt(FIELD_UPDATED_AT, beforeTimestamp),
            Filters.ne("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", true)
        )

        val update = Updates.set("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", true)

        collection.updateMany(query, update)
    }

    fun findAllCategoriesBySourceType(sourceType: Category.Source.Type): List<Category> {
        return collection.find(Filters.eq(FIELD_SOURCE_TYPE, sourceType.shortName))
            .map { doc -> fromDocument(doc) }
            .toList()
    }

    fun hasAnyCategoryOfSourceType(sourceType: Category.Source.Type): Boolean {
        return collection.find(Filters.eq(FIELD_SOURCE_TYPE, sourceType.shortName))
            .projection(Projections.include(FIELD_ID))
            .limit(1)
            .first() != null
    }

    fun getMaxId(): Int {
        val doc = collection.find()
            .sort(Sorts.descending(FIELD_ID))
            .projection(Projections.include(FIELD_ID))
            .limit(1)
            .first()
        return doc?.getInteger(FIELD_ID) ?: 0
    }

    fun clear() {
        collection.deleteMany(Document())
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
            id = doc.getInteger(FIELD_ID),
            source = Category.Source(
                type = Category.Source.Type.fromShortName(sourceDoc.getString("type")),
                id = sourceDoc.getString("id")
            ),
            parent = doc.getInteger(FIELD_PARENT),
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

