package com.gimlee.ads.persistence.model

import org.bson.types.ObjectId

data class WatchlistDocument(
    val id: ObjectId? = null,
    val userId: ObjectId,
    val adId: ObjectId,
    val createdAt: Long // Micros
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_AD_ID = "aid"
        const val FIELD_CREATED_AT = "cat"
    }
}
