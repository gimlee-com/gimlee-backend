package com.gimlee.payments.piratechain.persistence.model

import org.bson.types.ObjectId

/**
 * Represents the details of a single Pirate Chain Z-Address associated with a user.
 * Stores a hash of the view key, the salt used for hashing, and an update timestamp.
 */
data class PirateChainAddressInfo(
    val zAddress: String,
    val viewKeyHash: String,
    val viewKeySalt: String,
    val lastUpdateTimestamp: Long
) {
    companion object {
        const val FIELD_Z_ADDRESS = "addr"
        const val FIELD_VIEW_KEY_HASH = "vkh"
        const val FIELD_VIEW_KEY_SALT = "vks"
        const val FIELD_LAST_UPDATE_TIMESTAMP = "ts"
    }
}

/**
 * MongoDB document storing a user's Pirate Chain Z-addresses.
 * The document ID is the user's ID.
 */
data class UserPirateChainAddresses(
    val userId: ObjectId,
    val addresses: List<PirateChainAddressInfo> = listOf()
) {
    companion object {
        const val FIELD_USER_ID = "_id"
        const val FIELD_ADDRESSES = "a"
    }
}