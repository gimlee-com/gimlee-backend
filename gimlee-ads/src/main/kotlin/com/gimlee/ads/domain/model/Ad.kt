package com.gimlee.ads.domain.model

import java.time.Instant

/**
 * Represents an Ad in the core business domain.
 */
data class Ad(
    val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val price: CurrencyAmount?,
    val status: AdStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val location: Location?,
    val mediaPaths: List<String>? = emptyList(),
    val mainPhotoPath: String?,
    val stock: Int = 0,
    val lockedStock: Int = 0,
)