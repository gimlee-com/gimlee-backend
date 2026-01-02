package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction

data class SalesAdsRequestDto(
    val t: String? = null,
    val s: List<AdStatus>? = null,
    val by: By = By.CREATED_DATE,
    val dir: Direction = Direction.DESC,
    val p: Int = 0
)
