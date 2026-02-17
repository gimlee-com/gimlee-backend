package com.gimlee.ads.web

import com.gimlee.ads.domain.WatchlistService
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

import com.gimlee.ads.domain.AdOutcome
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@Tag(name = "Watchlist", description = "Endpoints for managing user's watchlist")
@RestController
@RequestMapping("/user/watchlist")
class WatchlistAdsController(
    private val watchlistService: WatchlistService,
    private val messageSource: MessageSource
) {

    @Operation(summary = "Add Ad to Watchlist")
    @ApiResponse(responseCode = "200", description = "Ad added to watchlist")
    @PostMapping("/{adId}")
    @Privileged("USER")
    fun addToWatchlist(
        @Parameter(description = "ID of the ad to add")
        @PathVariable adId: String
    ): ResponseEntity<StatusResponseDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        watchlistService.addToWatchlist(principal.userId, adId)
        
        val outcome = AdOutcome.ADDED_TO_WATCHLIST
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        
        return ResponseEntity.ok(StatusResponseDto(true, outcome.code, message))
    }

    @Operation(summary = "Remove Ad from Watchlist")
    @ApiResponse(responseCode = "200", description = "Ad removed from watchlist")
    @DeleteMapping("/{adId}")
    @Privileged("USER")
    fun removeFromWatchlist(
        @Parameter(description = "ID of the ad to remove")
        @PathVariable adId: String
    ): ResponseEntity<StatusResponseDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        watchlistService.removeFromWatchlist(principal.userId, adId)
        
        val outcome = AdOutcome.REMOVED_FROM_WATCHLIST
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        
        return ResponseEntity.ok(StatusResponseDto(true, outcome.code, message))
    }
}
