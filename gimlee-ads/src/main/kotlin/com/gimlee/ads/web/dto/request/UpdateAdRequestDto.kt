package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.common.domain.model.Currency
import com.gimlee.location.cities.data.cityDataById
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import java.math.BigDecimal


data class UpdateAdRequestDto(
    @field:Size(max = 100, message = "Title cannot exceed 100 characters.")
    @Schema(description = "Ad title. Max 100 characters.", example = "Handmade Leather Wallet")
    val title: String?,

    @field:Size(max = 5000, message = "Description cannot exceed 5000 characters.")
    @Schema(description = "Ad description. Max 5000 characters.", example = "Premium handcrafted leather wallet.")
    val description: String?,

    @Schema(
        description = "Pricing mode. Determines which pricing fields are required and allowed. " +
                "**FIXED_CRYPTO**: seller sets an explicit price per settlement currency via `fixedPrices`. " +
                "Fields `price`, `priceCurrency`, and `settlementCurrencies` must NOT be sent. " +
                "**PEGGED**: seller sets a reference `price` in `priceCurrency` with live conversion to `settlementCurrencies` at purchase time. " +
                "Field `fixedPrices` must NOT be sent. " +
                "If omitted on update, the existing mode is preserved.",
        example = "FIXED_CRYPTO"
    )
    val pricingMode: PricingMode?,

    @field:DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive.")
    @Schema(
        description = "Reference price amount. **PEGGED mode only.** Must be sent together with `priceCurrency`. " +
                "Sending this field when the effective pricing mode is FIXED_CRYPTO will result in a 400 error " +
                "(AD_FIXED_CRYPTO_CONFLICTING_PRICE).",
        example = "19.99"
    )
    val price: BigDecimal?,

    @Schema(
        description = "Currency of the reference price. **PEGGED mode only.** Required when `price` is provided. " +
                "Can be any currency (e.g. USD, EUR) — it does not need to be a settlement currency.",
        example = "USD"
    )
    val priceCurrency: Currency?,

    @Schema(
        description = "Per-currency fixed prices. **FIXED_CRYPTO mode only.** " +
                "Each key must be a valid settlement currency (e.g. ARRR, YEC) and each value must be a positive amount. " +
                "Settlement currencies are automatically derived from the keys of this map. " +
                "Sending this field when the effective pricing mode is PEGGED will result in a 400 error " +
                "(AD_PEGGED_CONFLICTING_FIXED_PRICES).",
        example = """{"ARRR": 100.00, "YEC": 5000.00}"""
    )
    val fixedPrices: Map<Currency, BigDecimal>?,

    @Schema(
        description = "Settlement currencies for the ad. **PEGGED mode only.** " +
                "Each currency must be a valid settlement currency that the seller has the required role for. " +
                "Sending this field when the effective pricing mode is FIXED_CRYPTO will result in a 400 error " +
                "(AD_FIXED_CRYPTO_CONFLICTING_SETTLEMENT) — use `fixedPrices` keys instead.",
        example = """["ARRR", "YEC"]"""
    )
    val settlementCurrencies: Set<Currency>?,

    @Schema(description = "Leaf category ID from the product taxonomy.", example = "5181")
    val categoryId: Int?,

    @field:Valid
    @Schema(description = "Ad location (city ID and/or geographic point).")
    val location: LocationDto?,

    @field:Size(max = 20, message = "Cannot have more than 20 media items.")
    @Schema(description = "List of media file paths. Max 20 items.")
    val mediaPaths: List<@Size(max = 255, message = "Media path too long.") String>?,

    @field:Size(max = 255, message = "Main photo path too long.")
    @Schema(description = "Path to the main photo. Must be one of the media paths.")
    val mainPhotoPath: String?,

    @Schema(description = "Number of items in stock. Must be positive for ad activation.", example = "10")
    val stock: Int?,

    @Schema(
        description = "Enable volatility protection. When enabled, settlement currencies experiencing " +
                "high price volatility are temporarily frozen (not available for purchase).",
        example = "true"
    )
    val volatilityProtection: Boolean?
) {
    fun toDomain(): UpdateAdRequest {
        return UpdateAdRequest(
            title = title,
            description = description,
            pricingMode = pricingMode,
            price = price?.let {
                require(priceCurrency != null) { "Price currency must be provided when the price is provided." }
                CurrencyAmount(price, priceCurrency)
            },
            fixedPrices = fixedPrices,
            settlementCurrencies = settlementCurrencies,
            location = location?.let { dto ->
                val point = dto.point ?: cityDataById[dto.cityId]?.let { doubleArrayOf(it.lon, it.lat) }
                requireNotNull(point) { "Location point is mandatory and city ID '${dto.cityId}' is invalid." }
                Location(cityId = dto.cityId, point = point)
            },
            categoryId = categoryId,
            mediaPaths = mediaPaths,
            mainPhotoPath = mainPhotoPath,
            stock = stock,
            volatilityProtection = volatilityProtection
        )
    }
}