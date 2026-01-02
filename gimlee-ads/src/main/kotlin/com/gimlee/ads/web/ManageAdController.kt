package com.gimlee.ads.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.web.dto.request.CreateAdRequestDto
import com.gimlee.ads.web.dto.request.UpdateAdRequestDto
import com.gimlee.ads.web.dto.response.AdDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.model.Role
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import jakarta.validation.Valid
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Ad Management", description = "Endpoints for creating, updating, and activating ads")
@RestController
@RequestMapping("/ads")
class ManageAdController(private val adService: AdService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "Create a New Ad",
        description = "Creates an ad in an INACTIVE state. Requires USER role authentication."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Ad created successfully",
        content = [Content(schema = Schema(implementation = AdDto::class))]
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
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while creating the ad."))
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
    @ApiResponse(responseCode = "404", description = "Ad not found for this user")
    @ApiResponse(responseCode = "400", description = "Invalid ad status or ID format")
    @ApiResponse(responseCode = "403", description = "Forbidden (e.g., using ARRR without PIRATE role)")
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Only users with the PIRATE role can make sales in ARRR. To get that role, you need to link your Pirate Chain wallet."))
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
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: AdService.AdOperationException) {
            log.warn("Update failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format for adId or userId
            log.warn("Update failed: Invalid ID format provided for ad {} by user {}", adId, principal.userId, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "Invalid ad ID format."))
        } catch (e: Exception) {
            log.error("Error updating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while updating the ad."))
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
    @ApiResponse(responseCode = "404", description = "Ad not found for this user")
    @ApiResponse(responseCode = "400", description = "Ad is already ACTIVE or invalid ID format")
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
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: AdService.AdOperationException) {
            log.warn("Activation failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format for adId
            log.warn("Activation failed: Invalid ID format provided for ad {} by user {}", adId, principal.userId, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "Invalid ad ID format."))
        } catch (e: Exception) {
            log.error("Error activating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while activating the ad."))
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
    @ApiResponse(responseCode = "404", description = "Ad not found for this user")
    @ApiResponse(responseCode = "400", description = "Ad is already INACTIVE or invalid ID format")
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
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: AdService.AdOperationException) {
            log.warn("Deactivation failed for ad {}: {} (requested by user {})", adId, e.message, principal.userId)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) { // Catch invalid ObjectId format for adId
            log.warn("Deactivation failed: Invalid ID format provided for ad {} by user {}", adId, principal.userId, e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "Invalid ad ID format."))
        } catch (e: Exception) {
            log.error("Error deactivating ad {} for user {}: {}", adId, principal.userId, e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred while deactivating the ad."))
        }
    }
}
