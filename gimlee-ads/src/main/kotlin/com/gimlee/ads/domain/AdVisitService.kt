package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdVisitRepository
import com.gimlee.ads.persistence.model.AdVisitDocument
import net.openhft.hashing.LongHashFunction
import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AdVisitService(
    private val adVisitRepository: AdVisitRepository
) {
    private val hasher = LongHashFunction.xx3()

    /**
     * Records a unique visit to an ad.
     * Uses xxHash64 to deduplicate visits from the same client on the same day.
     */
    fun recordVisit(adId: String, clientId: String, date: LocalDate = LocalDate.now()) {
        val dateInt = AdVisitDocument.toDateInt(date)
        val input = "$clientId|$adId|$dateInt"
        val hash = hasher.hashChars(input)
        
        adVisitRepository.recordVisit(adId, dateInt, hash)
    }

    /**
     * Gets the unique visit count for an ad within a date range.
     */
    fun getVisitCount(adId: String, startDate: LocalDate, endDate: LocalDate): Long {
        return adVisitRepository.getVisitCount(
            adId,
            AdVisitDocument.toDateInt(startDate),
            AdVisitDocument.toDateInt(endDate)
        )
    }

    /**
     * Gets daily visit counts for an ad within a date range.
     */
    fun getDailyVisits(adId: String, startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Int> {
        val documents = adVisitRepository.findDailyVisits(
            adId,
            AdVisitDocument.toDateInt(startDate),
            AdVisitDocument.toDateInt(endDate)
        )
        return documents.associate { AdVisitDocument.fromDateInt(it.dateInt) to it.count }
    }
}
