package com.gimlee.ads.domain.model

/**
 * Data class representing the fields to update for an Ad within the domain layer.
 */
data class UpdateAdRequest(
    val title: String? = null,
    val description: String? = null,
    val price: CurrencyAmount? = null,
    val location: Location? = null,
    val mediaPaths: List<String>? = null,
    val mainPhotoPath: String? = null,
    val stock: Int? = null,
)