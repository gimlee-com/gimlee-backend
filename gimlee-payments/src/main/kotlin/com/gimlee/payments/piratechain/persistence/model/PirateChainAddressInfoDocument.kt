package com.gimlee.payments.piratechain.persistence.model

import org.bson.types.ObjectId

/**
 * Represents the details of a single Pirate Chain Z-Address associated with a user.
 */
data class PirateChainAddressInfo(
    val zAddress: String,
    val viewKey: String,
) {
    companion object {
        const val FIELD_Z_ADDRESS = "addr"
        const val FIELD_VIEW_KEY = "vk"
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