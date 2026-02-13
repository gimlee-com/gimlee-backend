package com.gimlee.ads.domain.model

import java.time.LocalDate

/**
 * Represents daily unique visit analytics for an ad.
 */
data class AdVisit(
    val adId: String,
    val date: LocalDate,
    val count: Int
)
