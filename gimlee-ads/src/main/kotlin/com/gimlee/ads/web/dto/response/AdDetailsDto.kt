package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.web.dto.request.LocationDto
import java.math.BigDecimal
import com.gimlee.ads.model.Currency

data class AdDetailsDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: LocationDto?,
    val price: BigDecimal?,
    val currency: Currency?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
) {
    companion object {
        fun fromAd(ad: Ad): AdDetailsDto {
            return AdDetailsDto(
                id = ad.id,
                title = ad.title,
                description = ad.description,
                location = ad.location?.let { LocationDto(cityId = it.cityId, point = it.point) },
                price = ad.price,
                currency = ad.currency,
                mediaPaths = ad.mediaPaths,
                mainPhotoPath = ad.mainPhotoPath,
            )
        }
    }
}