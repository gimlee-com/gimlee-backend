package com.gimlee.ads.domain.model

/**
 * Data class representing the fields to update for an Ad within the domain layer.
 */
data class UpdateAdRequest(
    val title: String?,
    val description: String?,
    val price: CurrencyAmount?,
    val location: Location?,
    val mediaPaths: List<String>?,
    val mainPhotoPath: String?,
)