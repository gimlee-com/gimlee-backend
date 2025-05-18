package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad

data class AdPreviewDto(
    val id: String,
    val title: String,
    val price: CurrencyAmountDto? = null,
    val mainPhotoPath: String? = null,
    val location: LocationWithCityDetailsDto? = null,
) {
    companion object {
        fun fromAd(ad: Ad) = AdPreviewDto(
            id = ad.id,
            title = ad.title,
            price = CurrencyAmountDto.fromDomain(ad.price),
            mainPhotoPath = ad.mainPhotoPath,
            location = LocationWithCityDetailsDto.fromDomain(ad.location),
        )
    }
}