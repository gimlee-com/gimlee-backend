package com.gimlee.ads.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnore
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction // Use domain Direction
import com.gimlee.common.model.Range
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

class FetchAdsRequestDto(
    @field:Parameter(description = "Text search query")
    val t: String?,
    @field:Parameter(description = "City ID filter")
    val cty: String?,
    @field:Parameter(description = "Category ID filter (matches category or any of its subcategories)")
    val cat: Int?,
    @field:Parameter(description = "Center longitude for geographic search")
    val x: Double?,
    @field:Parameter(description = "Center latitude for geographic search")
    val y: Double?,
    @field:Parameter(description = "Radius in kilometers for geographic search (minimum 0.5km)")
    @field:DecimalMin("0.5") val r: Double?,
    @field:Parameter(description = "Sort by field")
    val by: By = By.CREATED_DATE,
    @field:Parameter(description = "Sort direction")
    val dir: Direction = Direction.DESC,
    @field:Parameter(description = "Minimum price filter")
    @field:DecimalMin("0.0") val minp: BigDecimal?,
    @field:Parameter(description = "Maximum price filter")
    @field:DecimalMax("1000000000.0") val maxp: BigDecimal?,
    @field:Parameter(description = "Page number (0-indexed)")
    val p: Int = 0 // page number
) {

    @field:JsonIgnore
    @field:Schema(hidden = true)
    val filters: AdFiltersDto = AdFiltersDto(
        text = t,
        cityId = cty,
        categoryId = cat,
        x = x,
        y = y,
        radius = r,
        priceRange = initPriceRange()
    )

    @field:JsonIgnore
    @field:Schema(hidden = true)
    val sorting: AdSortingDto = AdSortingDto(
        by = by,
        direction = dir
    )
    @field:JsonIgnore
    @field:Schema(hidden = true)
    val page: Int = p

    private fun initPriceRange() = if (null != minp || null != maxp) {
        Range(from = minp, to = maxp)
    } else {
        null
    }
}