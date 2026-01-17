package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import java.time.Instant

/**
 * DTO representing an advertisement for API responses.
 */
data class AdDto(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val price: CurrencyAmountDto?,
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: LocationWithCityDetailsDto?, // Changed type here
    val categoryId: String?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
    val stock: Int,
    val lockedStock: Int,
    val availableStock: Int,
) {
    companion object {
        fun fromDomain(ad: Ad): AdDto = with(ad) {
            AdDto(
                id = id,
                userId = userId,
                title = title,
                description = description,
                price = CurrencyAmountDto.fromDomain(price),
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                location = LocationWithCityDetailsDto.fromDomain(ad.location),
                categoryId = categoryId,
                mediaPaths = mediaPaths,
                mainPhotoPath = mainPhotoPath,
                stock = stock,
                lockedStock = lockedStock,
                availableStock = (stock - lockedStock).coerceAtLeast(0)
            )
        }
    }
}