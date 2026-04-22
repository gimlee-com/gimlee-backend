package com.gimlee.api.web.dto

import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CategoryPathElementDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.ads.web.dto.response.LocationWithCityDetailsDto
import com.gimlee.common.domain.model.Currency
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(
    description = "Per-settlement-currency price breakdown converted to the buyer's preferred currency. " +
        "Each entry maps a settlement cryptocurrency to its equivalent amount in the preferred fiat currency. " +
        "Use this to display a detailed price comparison across all payment options. " +
        "See also 'preferredPrice' for a single representative price suitable for summary views."
)
data class PreferredPricesDto(
    @Schema(description = "The buyer's preferred currency (e.g. USD, PLN)", example = "USD")
    val currency: Currency,
    @Schema(
        description = "Map of settlement currency ticker to the converted amount in the preferred currency. " +
            "Example: {\"ARRR\": 50.00, \"YEC\": 100.00} means 50 USD worth of ARRR and 100 USD worth of YEC.",
        example = "{\"ARRR\": 50.00, \"YEC\": 100.00}"
    )
    val prices: Map<Currency, BigDecimal>
)

@Schema(description = "Ad preview information with preferred currency price")
data class AdDiscoveryPreviewDto(
    val id: String,
    val title: String,
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmountDto? = null,
    val settlementCurrencies: Set<Currency> = emptySet(),
    val settlementPrices: List<CurrencyAmountDto>? = null,
    @Schema(
        description = "A single representative price converted to the buyer's preferred currency. " +
            "Best suited for card/list views where a single price summary is needed. " +
            "For ads with multiple settlement currencies, this is derived from the primary (first alphabetical) " +
            "settlement currency. For a full per-currency breakdown, use 'preferredPrices' instead."
    )
    val preferredPrice: CurrencyAmountDto? = null,
    @Schema(
        description = "Per-settlement-currency price breakdown in the buyer's preferred currency. " +
            "Present when the ad has settlement prices and the buyer's preferred currency is known. " +
            "Use this to show the buyer what each crypto payment option costs in their familiar currency. " +
            "For a single summary price, use 'preferredPrice' instead."
    )
    val preferredPrices: PreferredPricesDto? = null,
    val mainPhotoPath: String? = null,
    val categoryId: Int? = null,
    val categoryPath: List<CategoryPathElementDto>? = null,
    val location: LocationWithCityDetailsDto? = null,
    val frozenCurrencies: List<Currency> = emptyList(),
    val isBuyable: Boolean = true,
    val isWatched: Boolean? = null
) {
    companion object {
        fun fromAdPreview(
            preview: AdPreviewDto,
            preferredPrice: CurrencyAmountDto?,
            frozenCurrencies: List<Currency> = emptyList(),
            settlementPrices: List<CurrencyAmountDto>? = null,
            isWatched: Boolean? = null,
            preferredPrices: PreferredPricesDto? = null
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
                preferredPrices = preferredPrices,
                mainPhotoPath = preview.mainPhotoPath,
                categoryId = preview.categoryId,
                categoryPath = preview.categoryPath,
                location = preview.location,
                frozenCurrencies = frozenCurrencies,
                isBuyable = buyable,
                isWatched = isWatched
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
    @Schema(
        description = "A single representative price converted to the buyer's preferred currency. " +
            "Best suited for card/list views where a single price summary is needed. " +
            "For ads with multiple settlement currencies, this is derived from the primary (first alphabetical) " +
            "settlement currency. For a full per-currency breakdown, use 'preferredPrices' instead."
    )
    val preferredPrice: CurrencyAmountDto?,
    @Schema(
        description = "Per-settlement-currency price breakdown in the buyer's preferred currency. " +
            "Present when the ad has settlement prices and the buyer's preferred currency is known. " +
            "Use this to show the buyer what each crypto payment option costs in their familiar currency. " +
            "For a single summary price, use 'preferredPrice' instead."
    )
    val preferredPrices: PreferredPricesDto? = null,
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
    val isBuyable: Boolean = true,
    val isWatched: Boolean? = null
) {
    companion object {
        fun fromAdDetails(
            details: AdDetailsDto,
            preferredPrice: CurrencyAmountDto?,
            user: UserSpaceDetailsDto? = null,
            otherAds: List<AdDiscoveryPreviewDto>? = null,
            stats: AdDiscoveryStatsDto? = null,
            frozenCurrencies: List<Currency> = emptyList(),
            settlementPrices: List<CurrencyAmountDto>? = null,
            isWatched: Boolean? = null,
            preferredPrices: PreferredPricesDto? = null
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
                preferredPrices = preferredPrices,
                categoryId = details.categoryId,
                categoryPath = details.categoryPath,
                mediaPaths = details.mediaPaths,
                mainPhotoPath = details.mainPhotoPath,
                availableStock = details.availableStock,
                user = user,
                otherAds = otherAds,
                stats = stats,
                frozenCurrencies = frozenCurrencies,
                isBuyable = buyable,
                isWatched = isWatched
            )
        }
    }
}

@Schema(description = "Ad statistics")
data class AdDiscoveryStatsDto(
    @Schema(description = "Total number of views")
    val viewsCount: Long,
    @Schema(description = "Total number of users who have this ad in their watchlist")
    val watchersCount: Long? = null
)
