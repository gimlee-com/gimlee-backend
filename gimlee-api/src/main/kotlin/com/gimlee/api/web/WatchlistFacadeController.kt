package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.WatchlistService
import com.gimlee.api.service.AdEnrichmentService
import com.gimlee.api.service.AdEnrichmentType
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User Watchlist", description = "Endpoints for viewing user's watchlist with full ad details")
@RestController
@RequestMapping("/user/watchlist")
class WatchlistFacadeController(
    private val watchlistService: WatchlistService,
    private val adService: AdService,
    private val adEnrichmentService: AdEnrichmentService
) {

    @Operation(summary = "Fetch My Watched Ads", description = "Fetches full details of ads in the current user's watchlist.")
    @ApiResponse(responseCode = "200", description = "List of watched ads with details")
    @GetMapping("/ads")
    @Privileged("USER")
    fun getMyWatchedAds(): ResponseEntity<List<AdDiscoveryPreviewDto>> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val userId = principal.userId
        val watchedAdIds = watchlistService.getWatchlist(userId)
        
        if (watchedAdIds.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }

        val ads = adService.getAds(watchedAdIds)

        return ResponseEntity.ok(adEnrichmentService.enrichAdPreviews(
            ads,
            userId,
            setOf(
                AdEnrichmentType.PREFERRED_CURRENCY_PRICE,
                AdEnrichmentType.CATEGORY_PATH,
                AdEnrichmentType.WATCHLIST
            )
        ))
    }
}
