package com.gimlee.ads.domain.model

import com.gimlee.common.model.Range
import java.math.BigDecimal

data class AdFilters(
    val text: String? = null,
    val location: LocationFilter? = null,
    val createdBy: String? = null,
    val priceRange: Range<BigDecimal>? = null,
    val statuses: List<AdStatus>? = null,
    val categoryId: Int? = null
)
