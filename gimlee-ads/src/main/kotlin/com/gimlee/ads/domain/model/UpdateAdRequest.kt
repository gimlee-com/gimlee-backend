package com.gimlee.ads.domain.model

import com.gimlee.common.domain.model.Currency

/**
 * Data class representing the fields to update for an Ad within the domain layer.
 */
data class UpdateAdRequest(
    val title: String? = null,
    val description: String? = null,
    val pricingMode: PricingMode? = null,
    val price: CurrencyAmount? = null,
    val settlementCurrencies: Set<Currency>? = null,
    val location: Location? = null,
    val categoryId: Int? = null,
    val mediaPaths: List<String>? = null,
    val mainPhotoPath: String? = null,
    val stock: Int? = null,
    val volatilityProtection: Boolean? = null
)