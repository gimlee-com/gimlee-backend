package com.gimlee.ads.persistence.model

import com.gimlee.ads.domain.model.*
import com.gimlee.location.cities.data.cityDataById
import com.gimlee.common.InstantUtils.fromMicros
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import java.math.BigDecimal

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
    val mainPhotoPath: String?,
    val stock: Int = 0,
    val lockedStock: Int = 0
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
        const val FIELD_STOCK = "stk"
        const val FIELD_LOCKED_STOCK = "lstk"
    }

    /**
     * Converts this persistence document to the domain Ad object.
     */
    fun toDomain(): Ad {
        val domainPrice = if (price != null && currency != null) CurrencyAmount(price, currency) else null
        return Ad(
            id = id.toHexString(),
            userId = userId.toHexString(),
            title = title,
            description = description,
            price = domainPrice,
            status = status,
            createdAt = fromMicros(createdAtMicros),
            updatedAt = fromMicros(updatedAtMicros),
            location = if (cityId != null) {
                val point = location?.let { doubleArrayOf(it.x, it.y) }
                    ?: cityDataById[cityId]?.let { doubleArrayOf(it.lon, it.lat) }
                point?.let { Location(cityId, it) }
            } else {
                null
            },
            mediaPaths = mediaPaths,
            mainPhotoPath = mainPhotoPath,
            stock = stock,
            lockedStock = lockedStock
        )
    }
}