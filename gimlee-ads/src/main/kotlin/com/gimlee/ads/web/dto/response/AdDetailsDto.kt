package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.web.dto.request.LocationDto

data class AdDetailsDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: LocationDto?
) {
    companion object {
        fun fromAd(ad: Ad): AdDetailsDto {
            return AdDetailsDto(
                id = ad.id,
                title = ad.title,
                description = ad.description,
                location = ad.location?.let { LocationDto(cityId = it.cityId, point = it.point) }
            )
        }
    }
}