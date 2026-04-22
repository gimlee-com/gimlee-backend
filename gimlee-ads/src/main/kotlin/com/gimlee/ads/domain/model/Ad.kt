package com.gimlee.ads.domain.model

import com.gimlee.common.domain.model.Currency
import java.math.BigDecimal
import java.time.Instant

/**
 * Represents an Ad in the core business domain.
 */
data class Ad(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val pricingMode: PricingMode = PricingMode.FIXED_CRYPTO,
    val price: CurrencyAmount?,
    val fixedPrices: Map<Currency, BigDecimal> = emptyMap(),
    val settlementCurrencies: Set<Currency> = emptySet(),
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: Location?,
    val categoryId: Int? = null,
    val mediaPaths: List<String>? = emptyList(),
    val mainPhotoPath: String?,
    val stock: Int = 0,
    val lockedStock: Int = 0,
    val volatilityProtection: Boolean = false,
    val version: Long = 0L
) {
    val availableStock: Int get() = (stock - lockedStock).coerceAtLeast(0)
}