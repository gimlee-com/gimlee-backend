package com.gimlee.ads.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.*
import com.gimlee.ads.web.dto.request.AdFiltersDto.Companion.toAdFilters
import com.gimlee.ads.web.dto.request.AdSortingDto.Companion.toAdSorting
import com.gimlee.ads.web.dto.request.FetchAdsRequestDto
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Ad Discovery", description = "Endpoints for searching and viewing ads")
@RestController
class FetchAdsController(
    private val adService: AdService
) {
    companion object {
        private const val PAGE_SIZE = 60
    }

    @Operation(
        summary = "Fetch Ads",
        description = "Fetches ads with optional filters and sorting. Supports pagination."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of ads")
    @Validated
    @GetMapping(path = ["/ads/"])
    fun fetchAds(@Valid fetchAdsRequestDto: FetchAdsRequestDto): Page<AdPreviewDto> {
        val pageOfAds = adService.getAds(
            filters = toAdFilters(fetchAdsRequestDto.filters),
            sorting = toAdSorting(fetchAdsRequestDto.sorting),
            pageRequest = PageRequest.of(fetchAdsRequestDto.page, PAGE_SIZE)
        )
        return pageOfAds.map { AdPreviewDto.fromAd(it) }
    }

    @Operation(
        summary = "Fetch Featured Ads",
        description = "Fetches featured ads. Currently, these are the most recently added ads."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of featured ads")
    @GetMapping(path = ["/ads/featured/"])
    fun fetchFeaturedAds(): Page<AdPreviewDto> {
        val pageOfFeaturedAds = adService.getFeaturedAds()
        return pageOfFeaturedAds.map { AdPreviewDto.fromAd(it) }
    }


    @Operation(
        summary = "Fetch Single Ad Details",
        description = "Fetches details for a specific ad by its ID."
    )
    @ApiResponse(responseCode = "200", description = "Detailed ad information")
    @ApiResponse(responseCode = "404", description = "Ad not found")
    @GetMapping(path = ["/ads/{adId}"])
    fun fetchAd(
        @Parameter(description = "Unique ID of the ad")
        @PathVariable(name = "adId", required = true) adId: String,
        response: HttpServletResponse
    ): AdDetailsDto? {
        val ad = adService.getAd(adId)

        if (null == ad) {
            response.status = HttpStatus.NOT_FOUND.value()
            return null
        }

        if (ad.status != AdStatus.ACTIVE) {
            val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
            if (principal?.userId != ad.userId) {
                response.status = HttpStatus.NOT_FOUND.value()
                return null
            }
        }

        return AdDetailsDto.fromAd(ad)
    }
}