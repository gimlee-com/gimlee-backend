package com.gimlee.payments.piratechain.persistence

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.*
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_VIEW_KEY
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_Z_ADDRESS
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses.Companion.FIELD_ADDRESSES
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses.Companion.FIELD_USER_ID

@Repository
class UserPirateChainAddressRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-payments"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-user-addresses"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    /**
     * Finds the address mapping document for a given user ID.
     */
    fun findByUserId(userId: ObjectId): UserPirateChainAddresses? {
        val query = Filters.eq(FIELD_USER_ID, userId)
        return collection.find(query)
            .limit(1)
            .firstOrNull()?.toUserPirateChainAddresses() // Manual mapping
    }

    /**
     * Saves or updates the entire address mapping document for a user using replaceOne with upsert.
     * Use this carefully, prefer addAddressToUser for adding single addresses.
     * @return The updated/inserted UserPirateChainAddresses object.
     */
    fun save(userAddresses: UserPirateChainAddresses): UserPirateChainAddresses {
        val doc = mapToUserAddressesDocument(userAddresses)
        val filter = Filters.eq(FIELD_USER_ID, userAddresses.userId)
        val options = ReplaceOptions().upsert(true)
        collection.replaceOne(filter, doc, options)
        return userAddresses
    }

    /**
     * Adds new Pirate Chain address information to a user's document.
     * Uses $addToSet to avoid adding duplicates based on the entire PirateChainAddressInfo object.
     * Creates the document if it doesn't exist for the user (upsert).
     */
    fun addAddressToUser(userId: ObjectId, addressInfo: PirateChainAddressInfo) {
        val filter = Filters.eq(FIELD_USER_ID, userId)
        val addressInfoDoc = mapToAddressInfoDocument(addressInfo)
        val update = Updates.addToSet(FIELD_ADDRESSES, addressInfoDoc)
        val options = UpdateOptions().upsert(true)
        collection.updateOne(filter, update, options)
    }

    /**
     * Finds the user ID associated with a given Z-address.
     * Uses projection to only fetch the _id field.
     */
    fun findUserIdByAddress(zAddress: String): ObjectId? {
        // Query for documents where the 'addresses' array contains an element
        // matching the specified zAddress
        val query = Filters.eq("$FIELD_ADDRESSES.$FIELD_Z_ADDRESS", zAddress)
        val projection = Projections.include(FIELD_USER_ID)

        return collection.find(query)
            .projection(projection)
            .limit(1)
            .firstOrNull()
            ?.getObjectId(FIELD_USER_ID)
    }

    private fun mapToUserAddressesDocument(userAddresses: UserPirateChainAddresses): Document {
        return Document()
            .append(FIELD_USER_ID, userAddresses.userId)
            .append(FIELD_ADDRESSES, userAddresses.addresses.map { mapToAddressInfoDocument(it) })
    }

    private fun mapToAddressInfoDocument(addressInfo: PirateChainAddressInfo): Document {
        return Document()
            .append(FIELD_Z_ADDRESS, addressInfo.zAddress)
            .append(FIELD_VIEW_KEY, addressInfo.viewKey)
    }

    private fun Document.toPirateChainAddressInfo(): PirateChainAddressInfo {
        return PirateChainAddressInfo(
            zAddress = this.getString(FIELD_Z_ADDRESS),
            viewKey = this.getString(FIELD_VIEW_KEY)
        )
    }

    private fun Document.toUserPirateChainAddresses(): UserPirateChainAddresses {
        val addressDocs = this.getList(FIELD_ADDRESSES, Document::class.java) ?: listOf()
        val addresses = addressDocs.map { it.toPirateChainAddressInfo() }
        return UserPirateChainAddresses(
            userId = this.getObjectId(FIELD_USER_ID),
            addresses = addresses
        )
    }
}