package com.gimlee.api.service

import com.gimlee.ads.domain.model.Ad
import com.gimlee.api.web.dto.AdDiscoveryDetailsDto
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.common.domain.model.Currency

enum class AdEnrichmentType {
    PREFERRED_CURRENCY_PRICE,
    CATEGORY_PATH,
    USER_DETAILS,
    OTHER_ADS,
    STATS
}

interface AdEnrichmentService {
    fun enrichAdPreviews(
        ads: List<Ad>, 
        userId: String?,
        types: Set<AdEnrichmentType> = setOf(AdEnrichmentType.PREFERRED_CURRENCY_PRICE, AdEnrichmentType.CATEGORY_PATH)
    ): List<AdDiscoveryPreviewDto>

    fun enrichAdDetails(
        ad: Ad,
        userId: String?,
        types: Set<AdEnrichmentType> = setOf(
            AdEnrichmentType.PREFERRED_CURRENCY_PRICE, 
            AdEnrichmentType.CATEGORY_PATH,
            AdEnrichmentType.USER_DETAILS,
            AdEnrichmentType.OTHER_ADS,
            AdEnrichmentType.STATS
        )
    ): AdDiscoveryDetailsDto
    
    fun getPreferredCurrency(userId: String?): Currency
}
