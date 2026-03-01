package com.gimlee.api.web

import com.gimlee.ads.domain.AdOutcome
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.AdVisitService
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.web.dto.request.AdFiltersDto.Companion.toAdFilters
import com.gimlee.ads.web.dto.request.AdSortingDto.Companion.toAdSorting
import com.gimlee.ads.web.dto.request.FetchAdsRequestDto
import com.gimlee.api.service.AdEnrichmentService
import com.gimlee.api.service.AdEnrichmentType
import com.gimlee.api.web.dto.AdDiscoveryDetailsDto
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.api.web.dto.AdVisitStatsDto
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.annotation.Analytics
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Tag(name = "Ad Discovery", description = "Endpoints for searching and viewing ads")
@RestController
class AdDiscoveryController(
    private val adService: AdService,
    private val adVisitService: AdVisitService,
    private val messageSource: MessageSource,
    private val adEnrichmentService: AdEnrichmentService
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
        description = "Fetches ads with optional filters and sorting. Supports pagination and includes prices in preferred currency."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of ads")
    @Validated
    @Analytics(type = "AD_LIST_VIEW")
    @GetMapping(path = ["/ads/"])
    fun fetchAds(@Valid @ParameterObject fetchAdsRequestDto: FetchAdsRequestDto): Page<AdDiscoveryPreviewDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        val userId = principal?.userId

        val preferredCurrency = adEnrichmentService.getPreferredCurrency(userId)
        
        val filters = toAdFilters(fetchAdsRequestDto.filters).copy(
            preferredCurrency = preferredCurrency
        )

        val pageOfAds = adService.getAds(
            filters = filters,
            sorting = toAdSorting(fetchAdsRequestDto.sorting),
            pageRequest = PageRequest.of(fetchAdsRequestDto.page, PAGE_SIZE)
        )
        
        val enrichedDtos = adEnrichmentService.enrichAdPreviews(
            pageOfAds.content, 
            userId,
            setOf(
                AdEnrichmentType.PREFERRED_CURRENCY_PRICE,
                AdEnrichmentType.CATEGORY_PATH,
                AdEnrichmentType.WATCHLIST
            )
        )
        val dtoMap = enrichedDtos.associateBy { it.id }
        
        return pageOfAds.map { ad -> dtoMap[ad.id]!! }
    }

    @Operation(
        summary = "Fetch Featured Ads",
        description = "Fetches featured ads. Includes prices in preferred currency."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of featured ads")
    @Analytics(type = "FEATURED_ADS_VIEW")
    @GetMapping(path = ["/ads/featured/"])
    fun fetchFeaturedAds(): Page<AdDiscoveryPreviewDto> {
        val pageOfFeaturedAds = adService.getFeaturedAds()
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        val userId = principal?.userId
        
        val enrichedDtos = adEnrichmentService.enrichAdPreviews(
            pageOfFeaturedAds.content, 
            userId,
            setOf(
                AdEnrichmentType.PREFERRED_CURRENCY_PRICE,
                AdEnrichmentType.CATEGORY_PATH,
                AdEnrichmentType.WATCHLIST
            )
        )
        val dtoMap = enrichedDtos.associateBy { it.id }
        
        return pageOfFeaturedAds.map { ad -> dtoMap[ad.id]!! }
    }

    @Operation(
        summary = "Fetch Single Ad Details",
        description = "Fetches details for a specific ad by its ID. Includes price in preferred currency."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Detailed ad information",
        content = [Content(schema = Schema(implementation = AdDiscoveryDetailsDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @Analytics(type = "AD_VIEW", targetId = "#adId")
    @GetMapping(path = ["/ads/{adId}"])
    fun fetchAd(
        @PathVariable(required = true)
        @Parameter(description = "Unique ID of the ad")
        adId: String
    ): ResponseEntity<Any> {
        val ad = adService.getAd(adId) ?: return handleOutcome(AdOutcome.AD_NOT_FOUND)

        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        val userId = principal?.userId

        if (ad.status != AdStatus.ACTIVE) {
            if (userId != ad.userId) {
                return handleOutcome(AdOutcome.AD_NOT_FOUND)
            }
        }

        val enrichedAd = adEnrichmentService.enrichAdDetails(
            ad, 
            userId,
            setOf(
                AdEnrichmentType.PREFERRED_CURRENCY_PRICE,
                AdEnrichmentType.CATEGORY_PATH,
                AdEnrichmentType.USER_DETAILS,
                AdEnrichmentType.OTHER_ADS,
                AdEnrichmentType.STATS,
                AdEnrichmentType.WATCHLIST
            )
        )
        
        return ResponseEntity.ok(enrichedAd)
    }

    @Operation(summary = "Get visit statistics for an ad")
    @ApiResponse(responseCode = "200", description = "Visit statistics")
    @GetMapping("/ads/{adId}/stats")
    fun getStats(
        @Parameter(description = "Unique ID of the ad")
        @PathVariable(required = true)
        adId: String
    ): ResponseEntity<AdVisitStatsDto> {
        val now = LocalDate.now()
        val thirtyDaysAgo = now.minusDays(30)
        val startOfMonth = now.withDayOfMonth(1)
        val startOfYear = now.withDayOfYear(1)
        val beginningOfTime = LocalDate.of(2025, 1, 1)

        val dailyVisits = adVisitService.getDailyVisits(adId, thirtyDaysAgo, now)
        val formattedDaily = dailyVisits.mapKeys { it.key.format(DateTimeFormatter.ISO_LOCAL_DATE) }

        val monthly = adVisitService.getVisitCount(adId, startOfMonth, now)
        val yearly = adVisitService.getVisitCount(adId, startOfYear, now)
        val total = adVisitService.getVisitCount(adId, beginningOfTime, now)

        return ResponseEntity.ok(
            AdVisitStatsDto(
                daily = formattedDaily,
                monthly = monthly,
                yearly = yearly,
                total = total
            )
        )
    }
}
