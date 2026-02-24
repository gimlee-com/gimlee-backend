package com.gimlee.payments.crypto.persistence.model
import com.gimlee.common.domain.model.Currency

import org.bson.types.ObjectId

/**
 * Represents the details of a single cryptocurrency Z-Address associated with a user.
 * Stores the type, hash of the view key, the salt used for hashing, and an update timestamp.
 */
data class WalletAddressInfo(
    val type: Currency,
    val addressType: WalletShieldedAddressType,
    val zAddress: String,
    val viewKeyHash: String,
    val viewKeySalt: String,
    val lastUpdateTimestamp: Long
) {
    companion object {
        const val FIELD_TYPE = "t"
        const val FIELD_ADDRESS_TYPE = "at"
        const val FIELD_Z_ADDRESS = "addr"
        const val FIELD_VIEW_KEY_HASH = "vkh"
        const val FIELD_VIEW_KEY_SALT = "vks"
        const val FIELD_LAST_UPDATE_TIMESTAMP = "ts"
    }
}

enum class WalletShieldedAddressType(val rpcName: String) {
    SPROUT("sprout"),
    SAPLING("sapling"),
    ORCHARD("orchard");

    companion object {
        fun fromRpcName(rpcName: String): WalletShieldedAddressType =
            entries.firstOrNull { it.rpcName == rpcName.lowercase() }
                ?: throw IllegalArgumentException("Unknown shielded address type: $rpcName")
    }
}

/**
 * MongoDB document storing a user's cryptocurrency Z-addresses.
 * The document ID is the user's ID.
 */
data class UserWalletAddresses(
    val userId: ObjectId,
    val addresses: List<WalletAddressInfo> = listOf()
) {
    companion object {
        const val FIELD_USER_ID = "_id"
        const val FIELD_ADDRESSES = "a"
    }
}
