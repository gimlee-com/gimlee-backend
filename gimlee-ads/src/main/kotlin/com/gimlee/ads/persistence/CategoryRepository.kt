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
        const val FIELD_POPULARITY = "pop"
        const val FIELD_CREATED_AT = "crt"
        const val FIELD_UPDATED_AT = "upd"
        const val FIELD_DEPRECATED_FLAG = "dep" // Inside flags
        const val FIELD_DISPLAY_ORDER = "ord"
        const val FIELD_HIDDEN_FLAG = "hdn" // Inside flags
        const val FIELD_ADMIN_OVERRIDE_FLAG = "ao" // Inside flags
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

    fun resetAllPopularity() {
        collection.updateMany(Document(), Updates.set(FIELD_POPULARITY, 0L))
    }

    fun updatePopularity(categoryId: Int, count: Long) {
        collection.updateOne(Filters.eq(FIELD_ID, categoryId), Updates.set(FIELD_POPULARITY, count))
    }

    fun incrementPopularity(categoryIds: List<Int>, delta: Long) {
        if (categoryIds.isEmpty()) return
        collection.updateMany(Filters.`in`(FIELD_ID, categoryIds), Updates.inc(FIELD_POPULARITY, delta))
    }

    fun clear() {
        collection.deleteMany(Document())
    }

    fun findById(id: Int): Category? {
        return collection.find(Filters.eq(FIELD_ID, id))
            .limit(1)
            .firstOrNull()
            ?.let { fromDocument(it) }
    }

    fun existsById(id: Int): Boolean {
        return collection.find(Filters.eq(FIELD_ID, id))
            .projection(Projections.include(FIELD_ID))
            .limit(1)
            .first() != null
    }

    fun insert(
        id: Int,
        source: Category.Source,
        parent: Int?,
        nameMap: Map<String, Category.CategoryName>,
        displayOrder: Int,
        now: Long
    ): Boolean {
        val doc = Document()
            .append(FIELD_ID, id)
            .append(FIELD_SOURCE, Document("type", source.type.shortName).append("id", source.id))
            .append(FIELD_PARENT, parent)
            .append(FIELD_NAME, toNameDocument(nameMap))
            .append(FIELD_DISPLAY_ORDER, displayOrder)
            .append(FIELD_FLAGS, Document(FIELD_HIDDEN_FLAG, false).append(FIELD_ADMIN_OVERRIDE_FLAG, false).append(FIELD_DEPRECATED_FLAG, false))
            .append(FIELD_POPULARITY, 0L)
            .append(FIELD_CREATED_AT, now)
            .append(FIELD_UPDATED_AT, now)
        return try {
            collection.insertOne(doc)
            true
        } catch (e: com.mongodb.MongoWriteException) {
            false
        }
    }

    fun updateNameAndSlug(id: Int, nameMap: Map<String, Category.CategoryName>, now: Long) {
        val update = Updates.combine(
            Updates.set(FIELD_NAME, toNameDocument(nameMap)),
            Updates.set(FIELD_UPDATED_AT, now)
        )
        collection.updateOne(Filters.eq(FIELD_ID, id), update)
    }

    fun updateHidden(id: Int, hidden: Boolean) {
        collection.updateOne(
            Filters.eq(FIELD_ID, id),
            Updates.set("$FIELD_FLAGS.$FIELD_HIDDEN_FLAG", hidden)
        )
    }

    fun updateHiddenBulk(ids: List<Int>, hidden: Boolean) {
        if (ids.isEmpty()) return
        collection.updateMany(
            Filters.`in`(FIELD_ID, ids),
            Updates.set("$FIELD_FLAGS.$FIELD_HIDDEN_FLAG", hidden)
        )
    }

    fun updateDisplayOrder(id: Int, displayOrder: Int) {
        collection.updateOne(
            Filters.eq(FIELD_ID, id),
            Updates.set(FIELD_DISPLAY_ORDER, displayOrder)
        )
    }

    fun updateParent(id: Int, newParentId: Int?, newDisplayOrder: Int, now: Long) {
        val update = Updates.combine(
            Updates.set(FIELD_PARENT, newParentId),
            Updates.set(FIELD_DISPLAY_ORDER, newDisplayOrder),
            Updates.set(FIELD_UPDATED_AT, now)
        )
        collection.updateOne(Filters.eq(FIELD_ID, id), update)
    }

    fun deleteById(id: Int): Boolean {
        val result = collection.deleteOne(Filters.eq(FIELD_ID, id))
        return result.deletedCount > 0
    }

    fun getMaxDisplayOrderForParent(parentId: Int?): Int {
        val doc = collection.find(Filters.eq(FIELD_PARENT, parentId))
            .sort(Sorts.descending(FIELD_DISPLAY_ORDER))
            .projection(Projections.include(FIELD_DISPLAY_ORDER))
            .limit(1)
            .first()
        return (doc?.get(FIELD_DISPLAY_ORDER) as? Number)?.toInt() ?: -1
    }

    fun findSiblings(parentId: Int?): List<Category> {
        return collection.find(Filters.eq(FIELD_PARENT, parentId))
            .sort(Sorts.ascending(FIELD_DISPLAY_ORDER))
            .map { fromDocument(it) }
            .toList()
    }

    fun setAdminOverride(id: Int, override: Boolean) {
        collection.updateOne(
            Filters.eq(FIELD_ID, id),
            Updates.set("$FIELD_FLAGS.$FIELD_ADMIN_OVERRIDE_FLAG", override)
        )
    }

    fun hasAdminOverride(id: Int): Boolean {
        val doc = collection.find(Filters.eq(FIELD_ID, id))
            .projection(Projections.include(FIELD_FLAGS))
            .limit(1)
            .first() ?: return false
        val flags = doc.get(FIELD_FLAGS, Document::class.java) ?: return false
        return flags.getBoolean(FIELD_ADMIN_OVERRIDE_FLAG, false)
    }

    fun countChildren(id: Int): Int {
        return collection.countDocuments(Filters.eq(FIELD_PARENT, id)).toInt()
    }

    fun findAllByIds(ids: List<Int>): List<Category> {
        if (ids.isEmpty()) return emptyList()
        return collection.find(Filters.`in`(FIELD_ID, ids))
            .map { fromDocument(it) }
            .toList()
    }

    fun upsertCategoryBySourceTypeSkipName(
        sourceType: Category.Source.Type,
        id: Int,
        sourceId: String,
        parent: Int?,
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
            Updates.set(FIELD_UPDATED_AT, now),
            Updates.set("$FIELD_FLAGS.$FIELD_DEPRECATED_FLAG", false)
        )

        collection.updateOne(query, updates, UpdateOptions().upsert(true))
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
            popularity = (doc.get(FIELD_POPULARITY) as? Number)?.toLong() ?: 0L,
            displayOrder = (doc.get(FIELD_DISPLAY_ORDER) as? Number)?.toInt() ?: 0,
            hidden = flagsDoc.getBoolean(FIELD_HIDDEN_FLAG, false),
            adminOverride = flagsDoc.getBoolean(FIELD_ADMIN_OVERRIDE_FLAG, false),
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

