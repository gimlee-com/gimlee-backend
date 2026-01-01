package com.gimlee.user.persistence

import com.gimlee.user.domain.model.DeliveryAddress
import com.gimlee.user.persistence.model.DeliveryAddressDocument
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.COLLECTION_NAME
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_CITY
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_COUNTRY
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_CREATED_AT
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_FULL_NAME
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_ID
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_IS_DEFAULT
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_NAME
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_PHONE_NUMBER
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_POSTAL_CODE
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_STREET
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_UPDATED_AT
import com.gimlee.user.persistence.model.DeliveryAddressDocument.Companion.FIELD_USER_ID
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DeliveryAddressRepository(
    private val mongoDatabase: MongoDatabase
) {
    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(address: DeliveryAddress): DeliveryAddress {
        val doc = DeliveryAddressDocument.fromDomain(address)
        val bson = mapToBsonDocument(doc)
        val filter = Filters.eq(FIELD_ID, address.id)
        val options = ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
        return address
    }

    fun findById(id: UUID): DeliveryAddress? {
        val filter = Filters.eq(FIELD_ID, id)
        return collection.find(filter).firstOrNull()?.toDeliveryAddress()
    }

    fun findAllByUserId(userId: String): List<DeliveryAddress> {
        val filter = Filters.eq(FIELD_USER_ID, ObjectId(userId))
        return collection.find(filter).map { it.toDeliveryAddress() }.toList()
    }

    fun countByUserId(userId: String): Long {
        val filter = Filters.eq(FIELD_USER_ID, ObjectId(userId))
        return collection.countDocuments(filter)
    }

    fun deleteById(id: UUID) {
        val filter = Filters.eq(FIELD_ID, id)
        collection.deleteOne(filter)
    }

    private fun mapToBsonDocument(doc: DeliveryAddressDocument): Document {
        return Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_USER_ID, doc.userId)
            .append(FIELD_NAME, doc.name)
            .append(FIELD_FULL_NAME, doc.fullName)
            .append(FIELD_STREET, doc.street)
            .append(FIELD_CITY, doc.city)
            .append(FIELD_POSTAL_CODE, doc.postalCode)
            .append(FIELD_COUNTRY, doc.country)
            .append(FIELD_PHONE_NUMBER, doc.phoneNumber)
            .append(FIELD_IS_DEFAULT, doc.isDefault)
            .append(FIELD_CREATED_AT, doc.createdAtMicros)
            .append(FIELD_UPDATED_AT, doc.updatedAtMicros)
    }

    private fun Document.toDeliveryAddress(): DeliveryAddress {
        val doc = DeliveryAddressDocument(
            id = get(FIELD_ID, UUID::class.java),
            userId = get(FIELD_USER_ID, ObjectId::class.java),
            name = getString(FIELD_NAME),
            fullName = getString(FIELD_FULL_NAME),
            street = getString(FIELD_STREET),
            city = getString(FIELD_CITY),
            postalCode = getString(FIELD_POSTAL_CODE),
            country = getString(FIELD_COUNTRY),
            phoneNumber = getString(FIELD_PHONE_NUMBER),
            isDefault = getBoolean(FIELD_IS_DEFAULT),
            createdAtMicros = getLong(FIELD_CREATED_AT),
            updatedAtMicros = getLong(FIELD_UPDATED_AT)
        )
        return doc.toDomain()
    }
}
