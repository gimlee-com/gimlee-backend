package com.gimlee.api.service.impl

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.AdVisitService
import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.api.config.AdDiscoveryProperties
import com.gimlee.api.service.AdEnrichmentService
import com.gimlee.api.service.AdEnrichmentType
import com.gimlee.api.web.dto.AdDiscoveryDetailsDto
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.api.web.dto.AdDiscoveryStatsDto
import com.gimlee.api.web.dto.UserSpaceDetailsDto
import com.gimlee.auth.service.UserService
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.domain.service.CurrencyConverterService
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.web.dto.response.UserPresenceDto
import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class AdEnrichmentServiceImpl(
    private val categoryService: CategoryService,
    private val userPreferencesService: UserPreferencesService,
    private val currencyConverterService: CurrencyConverterService,
    private val userService: UserService,
    private val profileService: ProfileService,
    private val userPresenceService: UserPresenceService,
    private val adService: AdService,
    private val adVisitService: AdVisitService,
    private val adDiscoveryProperties: AdDiscoveryProperties
) : AdEnrichmentService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun enrichAdPreviews(
        ads: List<Ad>,
        userId: String?,
        types: Set<AdEnrichmentType>
    ): List<AdDiscoveryPreviewDto> {
        if (ads.isEmpty()) return emptyList()

        val preferredCurrency = if (types.contains(AdEnrichmentType.PREFERRED_CURRENCY_PRICE)) {
            getPreferredCurrency(userId)
        } else Currency.USD

        val categoryPaths = if (types.contains(AdEnrichmentType.CATEGORY_PATH)) {
            val categoryIds = ads.mapNotNull { it.categoryId }.toSet()
            categoryService.getFullCategoryPaths(categoryIds, LocaleContextHolder.getLocale().toLanguageTag())
        } else emptyMap()

        return ads.map { ad ->
            val categoryPath = ad.categoryId?.let { categoryPaths[it] }
            val previewDto = AdPreviewDto.fromAd(ad, categoryPath)

            val convertedPrice = if (types.contains(AdEnrichmentType.PREFERRED_CURRENCY_PRICE)) {
                convertPrice(ad.price, preferredCurrency)
            } else null

            AdDiscoveryPreviewDto.fromAdPreview(previewDto, convertedPrice)
        }
    }

    override fun enrichAdDetails(
        ad: Ad,
        userId: String?,
        types: Set<AdEnrichmentType>
    ): AdDiscoveryDetailsDto {
        val preferredCurrency = if (types.contains(AdEnrichmentType.PREFERRED_CURRENCY_PRICE)) {
            getPreferredCurrency(userId)
        } else Currency.USD

        val categoryPath = if (types.contains(AdEnrichmentType.CATEGORY_PATH) && ad.categoryId != null) {
            categoryService.getFullCategoryPath(ad.categoryId!!, LocaleContextHolder.getLocale().toLanguageTag())
        } else null

        val detailsDto = AdDetailsDto.fromAd(ad, categoryPath)
        val preferredPrice = if (types.contains(AdEnrichmentType.PREFERRED_CURRENCY_PRICE)) {
            convertPrice(ad.price, preferredCurrency)
        } else null

        var userDetails: UserSpaceDetailsDto? = null
        if (types.contains(AdEnrichmentType.USER_DETAILS)) {
            val adUserId = ad.userId
            val user = userService.findById(adUserId)
            val profile = profileService.getProfile(adUserId)
            val presence = userPresenceService.getUserPresence(adUserId)

            userDetails = user?.let {
                UserSpaceDetailsDto(
                    userId = adUserId,
                    username = it.username!!,
                    avatarUrl = profile?.avatarUrl,
                    presence = UserPresenceDto.fromDomain(presence),
                    memberSince = Instant.ofEpochSecond(it.id!!.timestamp.toLong()).toMicros()
                )
            }
        }

        var otherAdsDtos: List<AdDiscoveryPreviewDto> = emptyList()
        if (types.contains(AdEnrichmentType.OTHER_ADS)) {
            val otherAds = adService.getAds(
                filters = AdFilters(
                    createdBy = ad.userId,
                    excludeId = ad.id,
                    statuses = listOf(AdStatus.ACTIVE)
                ),
                sorting = AdSorting(By.CREATED_DATE, Direction.DESC),
                pageRequest = PageRequest.of(0, adDiscoveryProperties.otherAdsCount)
            )
            
            // Recursively enrich the "other ads", but typically only with basics
            otherAdsDtos = enrichAdPreviews(
                otherAds.content, 
                userId, 
                setOf(AdEnrichmentType.PREFERRED_CURRENCY_PRICE, AdEnrichmentType.CATEGORY_PATH)
            )
        }
        
        var stats: AdDiscoveryStatsDto? = null
        if (types.contains(AdEnrichmentType.STATS)) {
            val beginningOfTime = LocalDate.of(2025, 1, 1) // Should ideally be config or older
            val now = LocalDate.now()
            val totalViews = adVisitService.getVisitCount(ad.id!!, beginningOfTime, now)
            stats = AdDiscoveryStatsDto(viewsCount = totalViews)
        }

        return AdDiscoveryDetailsDto.fromAdDetails(detailsDto, preferredPrice, userDetails, otherAdsDtos, stats)
    }

    override fun getPreferredCurrency(userId: String?): Currency {
        var currencyCode: String? = null
        if (!userId.isNullOrBlank()) {
             try {
                currencyCode = userPreferencesService.getUserPreferences(userId).preferredCurrency
             } catch (e: Exception) {
                 log.debug("Could not fetch user preferences for user {}: {}", userId, e.message)
             }
        }
        
        if (currencyCode == null) {
            currencyCode = userPreferencesService.getDefaultCurrency()
        }
        
        return safeCurrency(currencyCode)
    }

    private fun safeCurrency(code: String?): Currency {
        return try {
            code?.let { Currency.valueOf(it) } ?: Currency.USD
        } catch (e: Exception) {
            log.warn("Invalid currency code: {}. Falling back to USD", code)
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
}
