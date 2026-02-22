package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.common.domain.model.Currency

data class AdPreviewDto(
    val id: String,
    val title: String,
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmountDto? = null,
    val settlementCurrencies: Set<Currency> = emptySet(),
    val mainPhotoPath: String? = null,
    val categoryId: Int? = null,
    val categoryPath: List<CategoryPathElementDto>? = null,
    val location: LocationWithCityDetailsDto? = null,
) {
    companion object {
        fun fromAd(ad: Ad, categoryPath: List<CategoryPathElementDto>? = null) = AdPreviewDto(
            id = ad.id,
            title = ad.title,
            pricingMode = ad.pricingMode,
            price = CurrencyAmountDto.fromDomain(ad.price),
            settlementCurrencies = ad.settlementCurrencies,
            mainPhotoPath = ad.mainPhotoPath,
            categoryId = ad.categoryId,
            categoryPath = categoryPath,
            location = LocationWithCityDetailsDto.fromDomain(ad.location),
        )
    }
}