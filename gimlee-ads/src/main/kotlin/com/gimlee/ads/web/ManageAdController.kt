package com.gimlee.ads.web
import com.gimlee.common.domain.model.Currency

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.*
import com.gimlee.ads.web.dto.request.CreateAdRequestDto
import com.gimlee.ads.web.dto.request.SalesAdsRequestDto
import com.gimlee.ads.web.dto.request.UpdateAdRequestDto
import com.gimlee.ads.web.dto.response.AdDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.model.Role
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.ads.domain.AdOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Ad Management", description = "Endpoints for creating, updating, and activating ads")
@RestController
@RequestMapping("/sales/ads")
class ManageAdController(
    private val adService: AdService,
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
        summary = "Fetch My Ads",
        description = "Fetches ads belonging to the authenticated user. Supports filtering and pagination."
    )
    @ApiResponse(responseCode = "200", description = "Paged list of user's ads")
    @GetMapping("/")
    @Privileged("USER")
    fun getMyAds(@Valid request: SalesAdsRequestDto): Page<AdDto> {
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
        return pageOfMyAds.map { AdDto.fromDomain(it) }
    }

    @Operation(
        summary = "Fetch Single Ad for Seller",
        description = "Fetches full details for a specific ad owned by the seller."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Detailed ad information",
        content = [Content(schema = Schema(implementation = AdDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found or not owned by the user. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{adId}")
    @Privileged("USER")
    fun getAd(
        @Parameter(description = "Unique ID of the ad")
        @PathVariable adId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val ad = adService.getAd(adId)

        if (ad == null || ad.userId != principal.userId) {
            return handleOutcome(AdOutcome.AD_NOT_FOUND)
        }

        return ResponseEntity.ok(AdDto.fromDomain(ad))
    }

    @Operation(
        summary = "Create a New Ad",
        description = "Creates an ad in an INACTIVE state. Requires USER role authentication."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Ad created successfully",
        content = [Content(schema = Schema(implementation = AdDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping
    @Privileged(role = "USER")
    fun createAd(@Valid @RequestBody request: CreateAdRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.Companion.getPrincipal()
        log.info("User {} attempting to create ad with title '{}'", principal.userId, request.title)
        return try {
            val createdAdDomain = adService.createAd(principal.userId, request.title)
            ResponseEntity.status(HttpStatus.CREATED).body(AdDto.Companion.fromDomain(createdAdDomain))
        } catch (e: Exception) {
            log.error("Error creating ad for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Update an Existing Ad",
        description = "Allows updating an INACTIVE ad. Requires USER role authentication."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ad updated successfully",
        content = [Content(schema = Schema(implementation = AdDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found for this user. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid ad status or ID format. Possible status codes: AD_INVALID_AD_STATUS, AD_INVALID_AD_ID",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden (e.g., using ARRR without PIRATE role). Possible status codes: AD_PIRATE_ROLE_REQUIRED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/{adId}")
    @Privileged(role = "USER")
    fun updateAd(
        @Parameter(description = "Unique ID of the ad to update")
        @PathVariable adId: String,
        @Valid @RequestBody request: UpdateAdRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.Companion.getPrincipal()
        log.info("User {} attempting to update ad {}", principal.userId, adId)

        if (request.currency == Currency.ARRR && !principal.roles.contains(Role.PIRATE)) {
            log.warn("User {} attempted to use ARRR without PIRATE role", principal.userId)
            return handleOutcome(AdOutcome.PIRATE_ROLE_REQUIRED)
        }

        return try {
            val updatedAdDomain = adService.updateAd(
                adId = adId,
                userId = principal.userId,
                updateData = request.toDomain()
            )
            ResponseEntity.ok(AdDto.Companion.fromDomain(updatedAdDomain))
        } catch (e: AdService.AdNotFoundException) {
            log.warn("Update failed: Ad {} not found (requested by user {})", adId, principal.userId)
            handleOutcome(AdOutcome.AD_NOT_FOUND)
        } catch (e: AdService.AdOperationException) {
            log.warn("Update failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_STATUS)
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format or other validation issues from toDomain()
            log.warn("Update failed: {} for ad {} by user {}", e.message, adId, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_ID)
        } catch (e: Exception) {
            log.error("Error updating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Activate an Ad",
        description = "Changes the status of an INACTIVE ad to ACTIVE. Requires USER role authentication."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ad activated successfully",
        content = [Content(schema = Schema(implementation = AdDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found for this user. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Ad is already ACTIVE or invalid ID format. Possible status codes: AD_INVALID_AD_STATUS, AD_INVALID_AD_ID",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/{adId}/activate")
    @Privileged(role = "USER")
    fun activateAd(
        @Parameter(description = "Unique ID of the ad to activate")
        @PathVariable adId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.Companion.getPrincipal()
        log.info("User {} attempting to activate ad {}", principal.userId, adId)
        return try {
            val activatedAdDomain = adService.activateAd(adId, principal.userId)
            ResponseEntity.ok(AdDto.fromDomain(activatedAdDomain))
        } catch (e: AdService.AdNotFoundException) {
            log.warn("Activation failed: Ad {} not found (requested by user {})", adId, principal.userId)
            handleOutcome(AdOutcome.AD_NOT_FOUND)
        } catch (e: AdService.AdOperationException) {
            log.warn("Activation failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_STATUS)
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format for adId
            log.warn("Activation failed: Invalid ID format {} for ad {} by user {}", e.message, adId, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_ID)
        } catch (e: Exception) {
            log.error("Error activating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Deactivate an Ad",
        description = "Changes the status of an ACTIVE ad to INACTIVE. Requires USER role authentication."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ad deactivated successfully",
        content = [Content(schema = Schema(implementation = AdDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found for this user. Possible status codes: AD_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Ad is already INACTIVE or invalid ID format. Possible status codes: AD_INVALID_AD_STATUS, AD_INVALID_AD_ID",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/{adId}/deactivate")
    @Privileged(role = "USER")
    fun deactivateAd(
        @Parameter(description = "Unique ID of the ad to deactivate")
        @PathVariable adId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.Companion.getPrincipal()
        log.info("User {} attempting to deactivate ad {}", principal.userId, adId)
        return try {
            val deactivatedAdDomain = adService.deactivateAd(adId, principal.userId)
            ResponseEntity.ok(AdDto.fromDomain(deactivatedAdDomain))
        } catch (e: AdService.AdNotFoundException) {
            log.warn("Deactivation failed: Ad {} not found (requested by user {})", adId, principal.userId)
            handleOutcome(AdOutcome.AD_NOT_FOUND)
        } catch (e: AdService.AdOperationException) {
            log.warn("Deactivation failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_STATUS)
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format for adId
            log.warn("Deactivation failed: Invalid ID format {} for ad {} by user {}", e.message, adId, principal.userId)
            handleOutcome(AdOutcome.INVALID_AD_ID)
        } catch (e: Exception) {
            log.error("Error deactivating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }
}
