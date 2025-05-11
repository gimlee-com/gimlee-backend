package com.gimlee.ads.web.dto.request

import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction


data class AdSortingDto(
    val by: By,
    val direction: Direction
) {
    companion object {
        fun toAdSorting(adSortingDto: AdSortingDto) = AdSorting(
            by = adSortingDto.by,
            direction = adSortingDto.direction
        )
    }
}