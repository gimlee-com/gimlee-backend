package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction
import io.swagger.v3.oas.annotations.Parameter

data class SalesAdsRequestDto(
    @field:Parameter(description = "Text search query")
    val t: String? = null,
    @field:Parameter(description = "Ad statuses to filter by")
    val s: List<AdStatus>? = null,
    @field:Parameter(description = "Sort by field")
    val by: By = By.CREATED_DATE,
    @field:Parameter(description = "Sort direction")
    val dir: Direction = Direction.DESC,
    @field:Parameter(description = "Page number (0-indexed)")
    val p: Int = 0
)
