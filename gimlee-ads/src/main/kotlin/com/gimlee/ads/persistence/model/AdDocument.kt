package com.gimlee.ads.persistence.model

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.model.AdStatus
import com.gimlee.ads.model.Currency
import com.gimlee.common.InstantUtils.fromMicros
import org.bson.types.ObjectId
import java.math.BigDecimal
import org.springframework.data.mongodb.core.geo.GeoJsonPoint

data class AdDocument(
    val id: ObjectId,
    val userId: ObjectId,
    val title: String,
    val description: String?,
    val price: BigDecimal?,
    val currency: Currency?,
    val status: AdStatus,
    val createdAtMicros: Long,
    val updatedAtMicros: Long,
    val cityId: String?,
    val location: GeoJsonPoint?,
    val mediaPaths: List<String>? = emptyList(),
    val mainPhotoPath: String?
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_TITLE = "t"
        const val FIELD_DESCRIPTION = "d"
        const val FIELD_PRICE = "p"
        const val FIELD_CURRENCY = "c"
        const val FIELD_STATUS = "s"
        const val FIELD_CREATED_AT = "crt"
        const val FIELD_UPDATED_AT = "upd"
        const val FIELD_CITY_ID = "cid"
        const val FIELD_LOCATION = "loc"
        const val FIELD_MEDIA_PATHS = "mep"
        const val FIELD_MAIN_PHOTO_PATH = "mpp"

        // Note: A 2dsphere index should be manually created on the 'loc' field in MongoDB
        // Example: db.getCollection('gimlee-ads-advertisements').createIndex({ "loc" : "2dsphere" })
        // Note: An index should be manually created on 'cid' if frequent city filtering is expected
        // Example: db.getCollection('gimlee-ads-advertisements').createIndex({ "cid" : 1 })
        // Note: An index should be manually created on 'crt' for sorting by creation date
        // Example: db.getCollection('gimlee-ads-advertisements').createIndex({ "crt" : -1 })
        // Note: An index should be manually created on 'uid' for fetching user's ads
        // Example: db.getCollection('gimlee-ads-advertisements').createIndex({ "uid" : 1 })

    }

    /**
     * Converts this persistence document to the domain Ad object.
     */
    fun toDomain(): Ad {
        val domainLocation = if (this.cityId != null && this.location != null) {
            Location(cityId = this.cityId, point = doubleArrayOf(this.location.x, this.location.y))
        } else if (this.cityId != null) {
            Location(cityId = this.cityId, point = doubleArrayOf())
        } else {
            null
        }

        return Ad(
            id = this.id.toHexString(),
            userId = this.userId.toHexString(),
            title = this.title,
            description = this.description,
            price = this.price,
            currency = this.currency,
            status = this.status,
            createdAt = fromMicros(this.createdAtMicros),
            updatedAt = fromMicros(this.updatedAtMicros),
            location = domainLocation,
            mediaPaths = this.mediaPaths,
            mainPhotoPath = this.mainPhotoPath
        )
    }
}