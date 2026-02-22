package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.common.domain.model.Currency

data class AdDetailsDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: LocationWithCityDetailsDto?,
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmountDto?,
    val settlementCurrencies: Set<Currency> = emptySet(),
    val categoryId: Int?,
    val categoryPath: List<CategoryPathElementDto>?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
    val availableStock: Int,
) {
    companion object {
        fun fromAd(ad: Ad, categoryPath: List<CategoryPathElementDto>? = null): AdDetailsDto {
            return AdDetailsDto(
                id = ad.id,
                title = ad.title,
                description = ad.description,
                location = LocationWithCityDetailsDto.fromDomain(ad.location),
                pricingMode = ad.pricingMode,
                price = CurrencyAmountDto.fromDomain(ad.price),
                settlementCurrencies = ad.settlementCurrencies,
                categoryId = ad.categoryId,
                categoryPath = categoryPath,
                mediaPaths = ad.mediaPaths,
                mainPhotoPath = ad.mainPhotoPath,
                availableStock = ad.availableStock,
            )
        }
    }
}