package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad

data class AdDetailsDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: LocationWithCityDetailsDto?,
    val price: CurrencyAmountDto?,
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
                price = CurrencyAmountDto.fromDomain(ad.price),
                categoryId = ad.categoryId,
                categoryPath = categoryPath,
                mediaPaths = ad.mediaPaths,
                mainPhotoPath = ad.mainPhotoPath,
                availableStock = ad.availableStock,
            )
        }
    }
}