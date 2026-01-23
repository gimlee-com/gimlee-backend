package com.gimlee.api.web.dto

import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.ads.web.dto.response.LocationWithCityDetailsDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ad preview information with preferred currency price")
data class AdDiscoveryPreviewDto(
    val id: String,
    val title: String,
    val price: CurrencyAmountDto? = null,
    val preferredPrice: CurrencyAmountDto? = null,
    val mainPhotoPath: String? = null,
    val categoryId: Int? = null,
    val categoryPath: List<CategoryPathElementDto>? = null,
    val location: LocationWithCityDetailsDto? = null,
) {
    companion object {
        fun fromAdPreview(preview: AdPreviewDto, preferredPrice: CurrencyAmountDto?): AdDiscoveryPreviewDto {
            return AdDiscoveryPreviewDto(
                id = preview.id,
                title = preview.title,
                price = preview.price,
                preferredPrice = preferredPrice,
                mainPhotoPath = preview.mainPhotoPath,
                categoryId = preview.categoryId,
                categoryPath = preview.categoryPath,
                location = preview.location
            )
        }
    }
}

@Schema(description = "Detailed ad information with preferred currency price")
data class AdDiscoveryDetailsDto(
    val id: String,
    val title: String,
    val description: String?,
    val location: LocationWithCityDetailsDto?,
    val price: CurrencyAmountDto?,
    val preferredPrice: CurrencyAmountDto?,
    val categoryId: Int?,
    val categoryPath: List<CategoryPathElementDto>?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
) {
    companion object {
        fun fromAdDetails(details: AdDetailsDto, preferredPrice: CurrencyAmountDto?): AdDiscoveryDetailsDto {
            return AdDiscoveryDetailsDto(
                id = details.id,
                title = details.title,
                description = details.description,
                location = details.location,
                price = details.price,
                preferredPrice = preferredPrice,
                categoryId = details.categoryId,
                categoryPath = details.categoryPath,
                mediaPaths = details.mediaPaths,
                mainPhotoPath = details.mainPhotoPath
            )
        }
    }
}
