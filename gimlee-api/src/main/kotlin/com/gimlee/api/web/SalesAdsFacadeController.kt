package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.AdVisitService
import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.web.dto.request.SalesAdsRequestDto
import com.gimlee.ads.web.dto.response.AdDto
import com.gimlee.api.web.dto.SalesAdDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.service.VolatilityStateService
import com.gimlee.purchases.persistence.PurchaseRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.springdoc.core.annotations.ParameterObject
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Ad Management", description = "Endpoints for creating, updating, and activating ads")
@RestController
@RequestMapping("/sales/ads")
class SalesAdsFacadeController(
    private val adService: AdService,
    private val categoryService: CategoryService,
    private val volatilityStateService: VolatilityStateService,
    private val adVisitService: AdVisitService,
    private val purchaseRepository: PurchaseRepository
) {

    companion object {
        private const val PAGE_SIZE = 60
    }

    @Operation(
        summary = "Fetch My Ads",
        description = "Fetches ads belonging to the authenticated user with seller-specific statistics " +
                "(view count, order count). Supports filtering, sorting, and pagination."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of user's ads with statistics")
    @GetMapping("/")
    @Privileged("USER")
    fun getMyAds(@Valid @ParameterObject request: SalesAdsRequestDto): Page<SalesAdDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val pageOfMyAds = adService.getAds(
            filters = AdFilters(
                createdBy = principal.userId,
                text = request.t,
                statuses = request.s ?: listOf(AdStatus.ACTIVE, AdStatus.INACTIVE)
            ),
            sorting = AdSorting(by = request.by, direction = request.dir),
            pageRequest = PageRequest.of(request.p, PAGE_SIZE)
        )

        if (pageOfMyAds.content.isEmpty()) {
            return PageImpl(emptyList(), pageOfMyAds.pageable, pageOfMyAds.totalElements)
        }

        val language = LocaleContextHolder.getLocale().toLanguageTag()
        val categoryIds = pageOfMyAds.content.mapNotNull { it.categoryId }.toSet()
        val categoryPaths = categoryService.getFullCategoryPaths(categoryIds, language)

        val adIds = pageOfMyAds.content.map { it.id }
        val adObjectIds = adIds.mapNotNull { runCatching { ObjectId(it) }.getOrNull() }

        val now = LocalDate.now()
        val viewCounts = adVisitService.getVisitCounts(adIds, LocalDate.EPOCH, now)
        val orderCounts = purchaseRepository.countOrdersByAdIds(adObjectIds)

        return pageOfMyAds.map { ad ->
            val frozenCurrencies = computeFrozenCurrencies(ad)
            val adDto = AdDto.fromDomain(ad, frozenCurrencies, ad.categoryId?.let { categoryPaths[it] })
            SalesAdDto(
                ad = adDto,
                viewsCount = viewCounts[ad.id] ?: 0L,
                ordersCount = orderCounts[ObjectId(ad.id)] ?: 0L
            )
        }
    }

    private fun computeFrozenCurrencies(ad: com.gimlee.ads.domain.model.Ad): List<Currency> {
        if (!ad.volatilityProtection) return emptyList()
        return ad.settlementCurrencies.filter { volatilityStateService.isFrozen(it) }
    }
}
