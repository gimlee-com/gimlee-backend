package com.gimlee.ads.domain.model

import com.gimlee.ads.model.Currency
import java.math.BigDecimal

/**
 * Data class representing the fields to update for an Ad within the domain layer.
 */
data class UpdateAdRequest(
    val title: String?,
    val description: String?,
    val price: BigDecimal?,
    val currency: Currency?,
    val location: Location?,
)