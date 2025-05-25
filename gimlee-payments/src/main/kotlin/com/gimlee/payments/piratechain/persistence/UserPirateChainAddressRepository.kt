package com.gimlee.payments.piratechain.persistence

import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_LAST_UPDATE_TIMESTAMP
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_VIEW_KEY_HASH
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_VIEW_KEY_SALT
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo.Companion.FIELD_Z_ADDRESS
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses.Companion.FIELD_ADDRESSES
import com.gimlee.payments.piratechain.persistence.model.UserPirateChainAddresses.Companion.FIELD_USER_ID
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

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

    fun findByUserId(userId: ObjectId): UserPirateChainAddresses? {
        val query = Filters.eq(FIELD_USER_ID, userId)
        return collection.find(query)
            .limit(1)
            .firstOrNull()?.toUserPirateChainAddresses()
    }

    fun save(userAddresses: UserPirateChainAddresses): UserPirateChainAddresses {
        val doc = mapToUserAddressesDocument(userAddresses)
        val filter = Filters.eq(FIELD_USER_ID, userAddresses.userId)
        val options = ReplaceOptions().upsert(true)
        collection.replaceOne(filter, doc, options)
        return userAddresses
    }

    /**
     * Attempts to update an existing address sub-document if its timestamp is older.
     * Returns true if an update occurred, false otherwise.
     */
    private fun tryUpdateExistingAddressWithOptimisticLock(userId: ObjectId, addressInfo: PirateChainAddressInfo): Boolean {
        val updateExistingFilter = Filters.and(
            Filters.eq(FIELD_USER_ID, userId),
            Filters.elemMatch(
                FIELD_ADDRESSES, Filters.and(
                    Filters.eq(FIELD_Z_ADDRESS, addressInfo.zAddress),
                    Filters.lt(FIELD_LAST_UPDATE_TIMESTAMP, addressInfo.lastUpdateTimestamp)
                )
            )
        )

        val updateOperations = Updates.combine(
            Updates.set("$FIELD_ADDRESSES.$.$FIELD_VIEW_KEY_HASH", addressInfo.viewKeyHash),
            Updates.set("$FIELD_ADDRESSES.$.$FIELD_VIEW_KEY_SALT", addressInfo.viewKeySalt),
            Updates.set("$FIELD_ADDRESSES.$.$FIELD_LAST_UPDATE_TIMESTAMP", addressInfo.lastUpdateTimestamp)
        )

        val updateResult: UpdateResult = collection.updateOne(updateExistingFilter, updateOperations)
        return updateResult.modifiedCount > 0
    }

    /**
     * Adds or updates Pirate Chain address information in a user's document using optimistic locking.
     * - If an address with the same zAddress exists and its stored timestamp is older than
     *   the timestamp in `addressInfo`, it's updated.
     * - If an address with the same zAddress does not exist, it's added to the user's addresses array.
     * - If the user document itself doesn't exist, it's created with the address.
     * - If an address with the same zAddress exists but its stored timestamp is NOT older,
     *   no modification occurs for that address (optimistic lock prevents overwrite of fresher data).
     */
    fun addAddressToUser(userId: ObjectId, addressInfo: PirateChainAddressInfo) {
        val updated = tryUpdateExistingAddressWithOptimisticLock(userId, addressInfo)

        if (!updated) {
            // If no update occurred (either zAddress not found, or its timestamp wasn't older, or user not found)
            // then attempt to ensure the user exists and add the address if it's not a duplicate.
            tryEnsureUserAndAddAddress(userId, addressInfo)
        }
    }

    /**
     * Ensures the user document exists (creating it if necessary with an empty addresses array)
     * and then attempts to add the new address to the user's document if it's not already present.
     * This is called if updating an existing address did not occur.
     */
    private fun tryEnsureUserAndAddAddress(userId: ObjectId, addressInfo: PirateChainAddressInfo) {
        // Step 1: Ensure the user document exists.
        // If it's new, initialize the addresses field as an empty list.
        // If it exists, $setOnInsert does nothing to existing fields.
        collection.updateOne(
            Filters.eq(FIELD_USER_ID, userId),
            Updates.setOnInsert(FIELD_ADDRESSES, emptyList<Document>()),
            UpdateOptions().upsert(true)
        )

        // Step 2: Now that the user document is guaranteed to exist,
        // add the addressInfo to the 'addresses' array, but only if an address
        // with the same zAddress is not already in that array.
        // No upsert is needed here because the document itself is confirmed to exist.
        val addAddressIfNotPresentFilter = Filters.and(
            Filters.eq(FIELD_USER_ID, userId),
            Filters.not(Filters.elemMatch(FIELD_ADDRESSES, Filters.eq(FIELD_Z_ADDRESS, addressInfo.zAddress)))
        )
        val addAddressUpdate = Updates.addToSet(FIELD_ADDRESSES, mapToAddressInfoDocument(addressInfo))

        collection.updateOne(addAddressIfNotPresentFilter, addAddressUpdate)
    }

    private fun mapToUserAddressesDocument(userAddresses: UserPirateChainAddresses): Document {
        return Document()
            .append(FIELD_USER_ID, userAddresses.userId)
            .append(FIELD_ADDRESSES, userAddresses.addresses.map { mapToAddressInfoDocument(it) })
    }

    private fun mapToAddressInfoDocument(addressInfo: PirateChainAddressInfo): Document {
        return Document()
            .append(FIELD_Z_ADDRESS, addressInfo.zAddress)
            .append(FIELD_VIEW_KEY_HASH, addressInfo.viewKeyHash)
            .append(FIELD_VIEW_KEY_SALT, addressInfo.viewKeySalt)
            .append(FIELD_LAST_UPDATE_TIMESTAMP, addressInfo.lastUpdateTimestamp)
    }

    private fun Document.toPirateChainAddressInfo(): PirateChainAddressInfo {
        return PirateChainAddressInfo(
            zAddress = this.getString(FIELD_Z_ADDRESS),
            viewKeyHash = this.getString(FIELD_VIEW_KEY_HASH),
            viewKeySalt = this.getString(FIELD_VIEW_KEY_SALT),
            lastUpdateTimestamp = this.getLong(FIELD_LAST_UPDATE_TIMESTAMP)
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