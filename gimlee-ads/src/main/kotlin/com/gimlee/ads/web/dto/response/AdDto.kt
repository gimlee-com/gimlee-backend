package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.model.AdStatus
import com.gimlee.ads.model.Currency
import com.gimlee.ads.web.dto.request.LocationDto // Import LocationDto
import java.math.BigDecimal
import java.time.Instant

/**
 * DTO representing an advertisement for API responses.
 */
data class AdDto(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val price: BigDecimal?,
    val currency: Currency?,
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: LocationDto?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
) {
    companion object {
        fun fromDomain(ad: Ad): AdDto = with(ad) {
            AdDto(
                id = id,
                userId = userId,
                title = title,
                description = description,
                price = price,
                currency = currency,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                location = location?.let { LocationDto(cityId = it.cityId, point = it.point) },
                mediaPaths = mediaPaths,
                mainPhotoPath = mainPhotoPath,
            )
        }
    }
}