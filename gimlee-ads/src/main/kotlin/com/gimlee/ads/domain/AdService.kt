package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.*
import com.gimlee.ads.model.AdStatus
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.common.toMicros
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AdService(private val adRepository: AdRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Thrown when an ad operation cannot be performed due to business rules. */
    class AdOperationException(message: String) : RuntimeException(message)

    /** Thrown when an ad is not found. */
    class AdNotFoundException(adId: String) : RuntimeException("Ad not found with ID: $adId")

    /**
     * Initiates a new ad with a title in the INACTIVE state. Location and other details are set via updateAd.
     */
    fun createAd(userId: String, title: String): Ad {
        val nowMicros = Instant.now().toMicros()
        val adDocument = AdDocument(
            id = ObjectId(),
            userId = ObjectId(userId),
            title = title,
            description = null,
            price = null,
            currency = null,
            status = AdStatus.INACTIVE,
            createdAtMicros = nowMicros,
            updatedAtMicros = nowMicros,
            cityId = null,
            location = null,
        )
        log.info("Creating new ad with title '{}' for user {}", title, userId)
        val savedDocument = adRepository.save(adDocument)
        return savedDocument.toDomain()
    }

    /**
     * Updates an existing ad. Handles location update.
     */
    fun updateAd(
        adId: String,
        userId: String,
        updateData: UpdateAdRequest
    ): Ad {
        val adObjectId = ObjectId(adId)
        val userObjectId = ObjectId(userId)
        val existingAdDoc = adRepository.findById(adObjectId)
            ?: throw AdNotFoundException(adId)

        if (existingAdDoc.userId != userObjectId) {
            log.warn("User {} attempted to update ad {} owned by {}", userId, adId, existingAdDoc.userId)
            throw AdOperationException("User does not have permission to update this ad.")
        }

        if (existingAdDoc.status != AdStatus.INACTIVE) {
            log.warn("Attempted to update ad {} which is not in INACTIVE state (current: {})", adId, existingAdDoc.status)
            throw AdOperationException("Ad can only be updated when in INACTIVE state.")
        }

        val newTitle = updateData.title ?: existingAdDoc.title // Use existing if null provided
        if (newTitle.isBlank()) {
             log.warn("Attempted to update ad {} with blank title", adId)
            throw AdOperationException("Title is mandatory and cannot be empty.")
        }

        val newCityId = updateData.location?.cityId
        val newGeoPoint = updateData.location?.point?.takeIf { it.size == 2 }?.let {
            GeoJsonPoint(it[0], it[1])
        }

        val updatedAdDoc = existingAdDoc.copy(
            title = newTitle,
            description = updateData.description ?: existingAdDoc.description,
            price = updateData.price ?: existingAdDoc.price,
            currency = updateData.currency ?: existingAdDoc.currency,
            cityId = newCityId ?: existingAdDoc.cityId,
            location = newGeoPoint ?: existingAdDoc.location,
            updatedAtMicros = Instant.now().toMicros()
        )

        log.info("Updating ad {} for user {}", adId, userId)
        val savedDocument = adRepository.save(updatedAdDoc)
        return savedDocument.toDomain()
    }

     /**
     * Retrieves a single ad by its ID.
     */
    fun getAd(adId: String): Ad? {
        val adObjectId = try {
            ObjectId(adId)
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid ad ID format received: {}", adId)
            return null
        }
        return adRepository.findById(adObjectId)?.toDomain()
    }

    /**
     * Retrieves ads based on filters, sorting, and pagination.
     */
    fun getAds(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): List<Ad> {
        // Ensure only ACTIVE ads are fetched unless 'createdBy' filter is present (for 'my ads')
        val effectiveFilters = if (filters.createdBy != null) {
            // If fetching user's ads, don't force ACTIVE status here, repo handles user filter
            filters
        } else {
            filters
        }
        log.debug("Fetching ads with filters: {}, sorting: {}, page: {}", effectiveFilters, sorting, pageRequest)
        return adRepository.find(effectiveFilters, sorting, pageRequest).map { it.toDomain() }
    }


    /**
     * Retrieves featured ads (currently most recent active ads).
     */
     fun getFeaturedAds(): List<Ad> {
        val filters = AdFilters()
        val sorting = AdSorting(by = By.CREATED_DATE, direction = Direction.DESC)
        val pageRequest = Pageable.ofSize(10)

        log.debug("Fetching featured ads")
        return adRepository.find(filters, sorting, pageRequest).map { it.toDomain() }
    }


    /**
     * Activates an ad, changing its status to ACTIVE.
     */
    fun activateAd(adId: String, userId: String): Ad {
        val adObjectId = ObjectId(adId)
        val userObjectId = ObjectId(userId)
        val existingAdDoc = adRepository.findById(adObjectId)
            ?: throw AdNotFoundException(adId)

        if (existingAdDoc.userId != userObjectId) {
            log.warn("User {} attempted to activate ad {} owned by {}", userId, adId, existingAdDoc.userId)
            throw AdOperationException("User does not have permission to activate this ad.")
        }

        if (existingAdDoc.status != AdStatus.INACTIVE) {
            log.warn("Attempted to activate ad {} which is not in INACTIVE state (current: {})", adId, existingAdDoc.status)
            throw AdOperationException("Ad can only be activated when in INACTIVE state.")
        }

        checkForCompleteness(existingAdDoc, adId)

        val activatedAdDoc = existingAdDoc.copy(
            status = AdStatus.ACTIVE,
            updatedAtMicros = Instant.now().toMicros()
        )

        log.info("Activating ad {} for user {}", adId, userId)
        val savedDocument = adRepository.save(activatedAdDoc)
        return savedDocument.toDomain()
    }

    private fun checkForCompleteness(existingAd: AdDocument, adId: String) {
        val isComplete = existingAd.title.isNotBlank() &&
                !existingAd.description.isNullOrBlank() &&
                existingAd.price != null &&
                existingAd.currency != null
                && (existingAd.cityId != null || existingAd.location != null)

        if (!isComplete) {
            log.warn(
                "Attempted to activate incomplete ad {}: title={}, desc={}, price={}, curr={}, location={}",
                adId,
                existingAd.title.isNotBlank(),
                !existingAd.description.isNullOrBlank(),
                existingAd.price != null,
                existingAd.currency != null,
                existingAd.cityId != null || existingAd.location != null
            )
            throw AdOperationException("Ad cannot be activated until title, description, price and location are all set.")
        }
    }
}