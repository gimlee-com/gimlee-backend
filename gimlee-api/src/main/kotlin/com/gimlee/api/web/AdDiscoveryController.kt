package com.gimlee.api.web

import com.gimlee.ads.domain.AdOutcome
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.AdVisitService
import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.web.dto.request.AdFiltersDto.Companion.toAdFilters
import com.gimlee.ads.web.dto.request.AdSortingDto.Companion.toAdSorting
import com.gimlee.ads.web.dto.request.FetchAdsRequestDto
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.api.config.AdDiscoveryProperties
import com.gimlee.api.web.dto.AdDiscoveryDetailsDto
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.api.web.dto.AdDiscoveryStatsDto
import com.gimlee.api.web.dto.AdVisitStatsDto
import com.gimlee.api.web.dto.UserSpaceDetailsDto
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.annotation.Analytics
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.payments.domain.service.CurrencyConverterService
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.web.dto.response.UserPresenceDto
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
import org.slf4j.LoggerFactory

@Tag(name = "Ad Discovery", description = "Endpoints for searching and viewing ads")
@RestController
class AdDiscoveryController(
    private val adService: AdService,
    private val adVisitService: AdVisitService,
    private val categoryService: CategoryService,
    private val userService: UserService,
    private val profileService: ProfileService,
    private val userPreferencesService: UserPreferencesService,
    private val userPresenceService: UserPresenceService,
    private val currencyConverterService: CurrencyConverterService,
    private val adDiscoveryProperties: AdDiscoveryProperties,
    private val messageSource: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        val preferredCurrency = getPreferredCurrency()
        val filters = toAdFilters(fetchAdsRequestDto.filters).copy(
            preferredCurrency = preferredCurrency
        )

        val pageOfAds = adService.getAds(
            filters = filters,
            sorting = toAdSorting(fetchAdsRequestDto.sorting),
            pageRequest = PageRequest.of(fetchAdsRequestDto.page, PAGE_SIZE)
        )
        
        val categoryIds = pageOfAds.content.mapNotNull { it.categoryId }.toSet()
        val categoryPaths = categoryService.getFullCategoryPaths(categoryIds, LocaleContextHolder.getLocale().toLanguageTag())
        
        return pageOfAds.map { ad ->
            val previewDto = AdPreviewDto.fromAd(ad, ad.categoryId?.let { id -> categoryPaths[id] })
            val preferredPrice = convertPrice(ad.price, preferredCurrency)
            AdDiscoveryPreviewDto.fromAdPreview(previewDto, preferredPrice)
        }
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
        val preferredCurrency = getPreferredCurrency()
        val categoryIds = pageOfFeaturedAds.content.mapNotNull { it.categoryId }.toSet()
        val categoryPaths = categoryService.getFullCategoryPaths(categoryIds, LocaleContextHolder.getLocale().toLanguageTag())
        
        return pageOfFeaturedAds.map { ad ->
            val previewDto = AdPreviewDto.fromAd(ad, ad.categoryId?.let { id -> categoryPaths[id] })
            val preferredPrice = convertPrice(ad.price, preferredCurrency)
            AdDiscoveryPreviewDto.fromAdPreview(previewDto, preferredPrice)
        }
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
        
        val preferredCurrency = getPreferredCurrency()
        val categoryPath = ad.categoryId?.let {
            categoryService.getFullCategoryPath(it, LocaleContextHolder.getLocale().toLanguageTag())
        }
        
        val detailsDto = AdDetailsDto.fromAd(ad, categoryPath)
        val preferredPrice = convertPrice(ad.price, preferredCurrency)
        
        val adUserId = ad.userId
        val user = userService.findById(adUserId)
        val profile = profileService.getProfile(adUserId)
        val presence = userPresenceService.getUserPresence(adUserId)

        val userDetails = user?.let {
            UserSpaceDetailsDto(
                userId = adUserId,
                username = it.username!!,
                avatarUrl = profile?.avatarUrl,
                presence = UserPresenceDto.fromDomain(presence),
                memberSince = Instant.ofEpochSecond(it.id!!.timestamp.toLong()).toMicros()
            )
        }

        val otherAds = adService.getAds(
            filters = AdFilters(
                createdBy = adUserId,
                excludeId = adId,
                statuses = listOf(AdStatus.ACTIVE)
            ),
            sorting = AdSorting(By.CREATED_DATE, Direction.DESC),
            pageRequest = PageRequest.of(0, adDiscoveryProperties.otherAdsCount)
        )

        val otherAdsCategoryIds = otherAds.content.mapNotNull { it.categoryId }.toSet()
        val otherAdsCategoryPaths = categoryService.getFullCategoryPaths(otherAdsCategoryIds, LocaleContextHolder.getLocale().toLanguageTag())

        val otherAdsDtos = otherAds.content.map { otherAd ->
            val previewDto = AdPreviewDto.fromAd(otherAd, otherAd.categoryId?.let { id -> otherAdsCategoryPaths[id] })
            val otherAdPreferredPrice = convertPrice(otherAd.price, preferredCurrency)
            AdDiscoveryPreviewDto.fromAdPreview(previewDto, otherAdPreferredPrice)
        }

        val beginningOfTime = LocalDate.of(2025, 1, 1)
        val now = LocalDate.now()
        val totalViews = adVisitService.getVisitCount(adId, beginningOfTime, now)
        val stats = AdDiscoveryStatsDto(views = totalViews)

        return ResponseEntity.ok(AdDiscoveryDetailsDto.fromAdDetails(detailsDto, preferredPrice, userDetails, otherAdsDtos, stats))
    }

    private fun getPreferredCurrency(): Currency {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        val currencyCode = if (!principal.isEmptyOrNull()) {
            userPreferencesService.getUserPreferences(principal!!.userId).preferredCurrency
        } else {
            userPreferencesService.getDefaultCurrency()
        }
        return try {
            currencyCode?.let { Currency.valueOf(it) } ?: Currency.USD
        } catch (e: Exception) {
            log.warn("Invalid preferred currency code: {}. Falling back to USD", currencyCode)
            Currency.USD
        }
    }

    private fun convertPrice(price: CurrencyAmount?, to: Currency): CurrencyAmountDto? {
        if (price == null) return null
        return try {
            val result = currencyConverterService.convert(price.amount, price.currency, to)
            CurrencyAmountDto(result.targetAmount, to)
        } catch (e: Exception) {
            log.error("Failed to convert price from {} to {}: {}", price.currency, to, e.message)
            null
        }
    }

    @Operation(summary = "Get visit statistics for an ad")
    @ApiResponse(responseCode = "200", description = "Visit statistics")
    @GetMapping("/ads/{adId}/stats")
    fun getStats(
        @Parameter(description = "Unique ID of the ad")
        @PathVariable(name = "adId", required = true) adId: String
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
