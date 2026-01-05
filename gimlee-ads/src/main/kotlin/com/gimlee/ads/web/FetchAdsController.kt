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
import com.gimlee.ads.domain.AdOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Ad Discovery", description = "Endpoints for searching and viewing ads")
@RestController
class FetchAdsController(
    private val adService: AdService,
    private val messageSource: MessageSource
) {
    companion object {
        private const val PAGE_SIZE = 60
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
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
    @ApiResponse(
        responseCode = "200",
        description = "Detailed ad information",
        content = [Content(schema = Schema(implementation = AdDetailsDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping(path = ["/ads/{adId}"])
    fun fetchAd(
        @Parameter(description = "Unique ID of the ad")
        @PathVariable(name = "adId", required = true) adId: String
    ): ResponseEntity<Any> {
        val ad = adService.getAd(adId)

        if (null == ad) {
            return handleOutcome(AdOutcome.AD_NOT_FOUND)
        }

        if (ad.status != AdStatus.ACTIVE) {
            val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
            if (principal?.userId != ad.userId) {
                return handleOutcome(AdOutcome.AD_NOT_FOUND)
            }
        }

        return ResponseEntity.ok(AdDetailsDto.fromAd(ad))
    }
}