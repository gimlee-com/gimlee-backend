package com.gimlee.ads.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.web.dto.request.AdFiltersDto.Companion.toAdFilters
import com.gimlee.ads.web.dto.request.AdSortingDto.Companion.toAdSorting
import com.gimlee.ads.web.dto.request.FetchAdsRequestDto
import com.gimlee.ads.web.dto.response.AdDetailsDto
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
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

@RestController
class FetchAdsController(
    private val adService: AdService
) {
    companion object {
        private const val PAGE_SIZE = 60
    }

    /**
     * Endpoint for fetching ads with filters and sorting.
     * Note: This endpoint is not protected by any role, so anyone can call it.
     */
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

    /**
     * Non-protected endpoint for fetching featured ads.
     * Note: This endpoint is not protected by any role, so anyone can call it.
     * Note #2: As of now, the "featured ads" are recently added ads - a proper algo will follow in the future.
     */
    @GetMapping(path = ["/ads/featured"])
    fun fetchFeaturedAds(): Page<AdPreviewDto> {
        val pageOfFeaturedAds = adService.getFeaturedAds()
        return pageOfFeaturedAds.map { AdPreviewDto.fromAd(it) }
    }

    /**
     * Protected endpoint for fetching my ads.
     * Note: This endpoint is protected by role, so only authenticated users can call this endpoint.
     */
    @GetMapping(path = ["/ads/my"])
    @Privileged("USER")
    fun fetchMyAds(): Page<AdPreviewDto> {
        val pageOfMyAds = adService.getAds(
            filters = AdFilters(createdBy = HttpServletRequestAuthUtil.getPrincipal().userId),
            sorting = AdSorting(by = By.CREATED_DATE, direction = Direction.DESC),
            pageRequest = Pageable.unpaged() // This will result in a Page with all user's ads
        )
        return pageOfMyAds.map { AdPreviewDto.fromAd(it) }
    }

    @GetMapping(path = ["/ads/{adId}"])
    fun fetchAd(
        @PathVariable(name = "adId", required = true) adId: String,
        response: HttpServletResponse
    ): AdDetailsDto? {
        val ad = adService.getAd(adId)

        if (null == ad) {
            response.status = HttpStatus.NOT_FOUND.value()
            return null
        }
        return AdDetailsDto.fromAd(ad)
    }
}