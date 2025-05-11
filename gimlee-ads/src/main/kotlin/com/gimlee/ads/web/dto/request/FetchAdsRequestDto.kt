package com.gimlee.ads.web.dto.request

import com.fasterxml.jackson.annotation.JsonIgnore
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction // Use domain Direction
import com.gimlee.common.model.Range
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
// import org.springframework.data.domain.Sort // Not needed directly if using domain Direction
import java.math.BigDecimal

class FetchAdsRequestDto(
    val t: String?,
    val cty: String?,
    val x: Double?,
    val y: Double?,
    @field:DecimalMin("0.5") val r: Double?,
    val by: By = By.CREATED_DATE,
    val dir: Direction = Direction.DESC,
    @field:DecimalMin("0.0") val minp: BigDecimal?,
    @field:DecimalMax("1000000000.0") val maxp: BigDecimal?,
    val p: Int = 0 // page number
) {

    @field:JsonIgnore
    val filters: AdFiltersDto = AdFiltersDto(
        text = t,
        cityId = cty,
        x = x,
        y = y,
        radius = r,
        priceRange = initPriceRange()
    )

    @field:JsonIgnore
    val sorting: AdSortingDto = AdSortingDto(
        by = by,
        direction = dir
    )
    @field:JsonIgnore
    val page: Int = p

    private fun initPriceRange() = if (null != minp || null != maxp) {
        Range(from = minp, to = maxp)
    } else {
        null
    }
}