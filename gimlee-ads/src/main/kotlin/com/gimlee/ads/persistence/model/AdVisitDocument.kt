package com.gimlee.ads.persistence.model

import com.gimlee.ads.domain.model.AdVisit
import org.bson.types.ObjectId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

data class AdVisitDocument(
    val id: ObjectId? = null,
    val adId: ObjectId,
    val dateInt: Int, // YYYYMMDD
    val count: Int,
    val expirationDate: Date? = null
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_AD_ID = "aid"
        const val FIELD_DATE = "d"
        const val FIELD_COUNT = "c"
        const val FIELD_EXPIRATION_DATE = "exp"

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun toDateInt(date: LocalDate): Int = date.format(DATE_FORMATTER).toInt()
        fun fromDateInt(dateInt: Int): LocalDate = LocalDate.parse(dateInt.toString(), DATE_FORMATTER)
    }

    fun toDomain(): AdVisit = AdVisit(
        adId = adId.toHexString(),
        date = fromDateInt(dateInt),
        count = count
    )
}
