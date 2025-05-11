package com.gimlee.ads.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.web.dto.request.CreateAdRequestDto
import com.gimlee.ads.web.dto.request.UpdateAdRequestDto
import com.gimlee.ads.web.dto.response.AdDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ads")
class ManageAdController(private val adService: AdService) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Endpoint for authenticated users to create a new (INACTIVE) ad.
     */
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

    /**
     * Endpoint for authenticated users to update an INACTIVE ad.
     */
    @PutMapping("/{adId}")
    @Privileged(role = "USER")
    fun updateAd(
        @PathVariable adId: String,
        @Valid @RequestBody request: UpdateAdRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.Companion.getPrincipal()
        log.info("User {} attempting to update ad {}", principal.userId, adId)
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

    /**
     * Endpoint for authenticated users to activate an INACTIVE ad.
     */
    @PostMapping("/{adId}/activate")
    @Privileged(role = "USER")
    fun activateAd(@PathVariable adId: String): ResponseEntity<Any> {
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
}
