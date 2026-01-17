package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad

data class AdPreviewDto(
    val id: String,
    val title: String,
    val price: CurrencyAmountDto? = null,
    val mainPhotoPath: String? = null,
    val categoryId: Int? = null,
    val categoryPath: List<CategoryPathElementDto>? = null,
    val location: LocationWithCityDetailsDto? = null,
) {
    companion object {
        fun fromAd(ad: Ad, categoryPath: List<CategoryPathElementDto>? = null) = AdPreviewDto(
            id = ad.id,
            title = ad.title,
            price = CurrencyAmountDto.fromDomain(ad.price),
            mainPhotoPath = ad.mainPhotoPath,
            categoryId = ad.categoryId,
            categoryPath = categoryPath,
            location = LocationWithCityDetailsDto.fromDomain(ad.location),
        )
    }
}