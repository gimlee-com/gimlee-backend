package com.gimlee.api.web.dto

import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.ads.web.dto.response.LocationWithCityDetailsDto
import com.gimlee.common.domain.model.Currency
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ad preview information with preferred currency price")
data class AdDiscoveryPreviewDto(
    val id: String,
    val title: String,
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmountDto? = null,
    val settlementCurrencies: Set<Currency> = emptySet(),
    val settlementPrices: List<CurrencyAmountDto>? = null,
    val preferredPrice: CurrencyAmountDto? = null,
    val mainPhotoPath: String? = null,
    val categoryId: Int? = null,
    val categoryPath: List<CategoryPathElementDto>? = null,
    val location: LocationWithCityDetailsDto? = null,
    val frozenCurrencies: List<Currency> = emptyList(),
    val isBuyable: Boolean = true
) {
    companion object {
        fun fromAdPreview(
            preview: AdPreviewDto,
            preferredPrice: CurrencyAmountDto?,
            frozenCurrencies: List<Currency> = emptyList(),
            settlementPrices: List<CurrencyAmountDto>? = null
        ): AdDiscoveryPreviewDto {
            val buyable = frozenCurrencies.size < preview.settlementCurrencies.size
            return AdDiscoveryPreviewDto(
                id = preview.id,
                title = preview.title,
                pricingMode = preview.pricingMode,
                price = preview.price,
                settlementCurrencies = preview.settlementCurrencies,
                settlementPrices = settlementPrices,
                preferredPrice = preferredPrice,
                mainPhotoPath = preview.mainPhotoPath,
                categoryId = preview.categoryId,
                categoryPath = preview.categoryPath,
                location = preview.location,
                frozenCurrencies = frozenCurrencies,
                isBuyable = buyable
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
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmountDto?,
    val settlementCurrencies: Set<Currency> = emptySet(),
    val settlementPrices: List<CurrencyAmountDto>? = null,
    val preferredPrice: CurrencyAmountDto?,
    val categoryId: Int?,
    val categoryPath: List<CategoryPathElementDto>?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
    val availableStock: Int,
    val user: UserSpaceDetailsDto? = null,
    @Schema(description = "List of most recent other ads from the same user")
    val otherAds: List<AdDiscoveryPreviewDto>? = null,
    val stats: AdDiscoveryStatsDto? = null,
    val frozenCurrencies: List<Currency> = emptyList(),
    val isBuyable: Boolean = true
) {
    companion object {
        fun fromAdDetails(
            details: AdDetailsDto,
            preferredPrice: CurrencyAmountDto?,
            user: UserSpaceDetailsDto? = null,
            otherAds: List<AdDiscoveryPreviewDto>? = null,
            stats: AdDiscoveryStatsDto? = null,
            frozenCurrencies: List<Currency> = emptyList(),
            settlementPrices: List<CurrencyAmountDto>? = null
        ): AdDiscoveryDetailsDto {
            val buyable = frozenCurrencies.size < details.settlementCurrencies.size
            return AdDiscoveryDetailsDto(
                id = details.id,
                title = details.title,
                description = details.description,
                location = details.location,
                pricingMode = details.pricingMode,
                price = details.price,
                settlementCurrencies = details.settlementCurrencies,
                settlementPrices = settlementPrices,
                preferredPrice = preferredPrice,
                categoryId = details.categoryId,
                categoryPath = details.categoryPath,
                mediaPaths = details.mediaPaths,
                mainPhotoPath = details.mainPhotoPath,
                availableStock = details.availableStock,
                user = user,
                otherAds = otherAds,
                stats = stats,
                frozenCurrencies = frozenCurrencies,
                isBuyable = buyable
            )
        }
    }
}

@Schema(description = "Ad statistics")
data class AdDiscoveryStatsDto(
    @Schema(description = "Total number of views")
    val viewsCount: Long
)
