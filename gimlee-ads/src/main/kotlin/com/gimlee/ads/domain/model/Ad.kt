package com.gimlee.ads.domain.model

import com.gimlee.ads.model.AdStatus
import com.gimlee.ads.model.Currency
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
    val price: BigDecimal?,
    val currency: Currency?,
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: Location?,
    val mediaPaths: List<String>? = emptyList(),
    val mainPhotoPath: String?,
)