package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.LocationFilter
import com.gimlee.common.model.Range
import org.springframework.data.geo.Circle
import org.springframework.data.geo.Point
import java.math.BigDecimal

data class AdFiltersDto(
    val text: String?,
    val priceRange: Range<BigDecimal>?,
    val cityId: String?,
    val x: Double?,
    val y: Double?,
    val radius: Double?,
    val categoryId: Int?
) {
    companion object {
        private const val KM_TO_RADIANS = 1 / 6378.1

        fun toAdFilters(adFiltersDto: AdFiltersDto): AdFilters {
            val locationFilter = if (adFiltersDto.cityId != null) {
                LocationFilter(cityIds = listOf(adFiltersDto.cityId), circle = null)
            } else if (adFiltersDto.x != null && adFiltersDto.y != null && adFiltersDto.radius != null) {
                val centerPoint = Point(adFiltersDto.x, adFiltersDto.y)
                val radiusInRadians = adFiltersDto.radius * KM_TO_RADIANS
                LocationFilter(cityIds = null, circle = Circle(centerPoint, radiusInRadians))
            } else {
                null
            }

            return AdFilters(
                text = adFiltersDto.text,
                priceRange = adFiltersDto.priceRange,
                location = locationFilter,
                categoryId = adFiltersDto.categoryId
            )
        }
    }
}