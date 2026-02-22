package com.gimlee.ads.web.dto.response

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.common.domain.model.Currency
import java.time.Instant

/**
 * DTO representing an advertisement for API responses.
 */
data class AdDto(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val pricingMode: PricingMode,
    val price: CurrencyAmountDto?,
    val settlementCurrencies: Set<Currency>,
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: LocationWithCityDetailsDto?,
    val categoryId: Int?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
    val stock: Int,
    val lockedStock: Int,
    val availableStock: Int,
    val volatilityProtection: Boolean,
    val frozenCurrencies: List<Currency> = emptyList(),
    val isBuyable: Boolean = true
) {
    companion object {
        fun fromDomain(ad: Ad, frozenCurrencies: List<Currency> = emptyList()): AdDto = with(ad) {
            val buyable = frozenCurrencies.size < settlementCurrencies.size || !volatilityProtection
            AdDto(
                id = id,
                userId = userId,
                title = title,
                description = description,
                pricingMode = pricingMode,
                price = CurrencyAmountDto.fromDomain(price),
                settlementCurrencies = settlementCurrencies,
                status = status,
                createdAt = createdAt,
                updatedAt = updatedAt,
                location = LocationWithCityDetailsDto.fromDomain(ad.location),
                categoryId = categoryId,
                mediaPaths = mediaPaths,
                mainPhotoPath = mainPhotoPath,
                stock = stock,
                lockedStock = lockedStock,
                availableStock = (stock - lockedStock).coerceAtLeast(0),
                volatilityProtection = volatilityProtection,
                frozenCurrencies = frozenCurrencies,
                isBuyable = buyable
            )
        }
    }
}