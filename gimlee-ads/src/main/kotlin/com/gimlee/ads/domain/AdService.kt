package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.*
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.common.toMicros
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.stereotype.Service
import java.time.Instant


@Service
class AdService(
    private val adRepository: AdRepository,
    private val adStockService: AdStockService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Thrown when an ad operation cannot be performed due to business rules. */
    class AdOperationException(message: String) : RuntimeException(message)

    /** Thrown when an ad is not found. */
    class AdNotFoundException(adId: String) : RuntimeException("Ad not found with ID: $adId")

    /**
     * Initiates a new ad with a title in the INACTIVE state. Location and other details are set via updateAd.
     */
    fun createAd(userId: String, title: String, stock: Int = 0): Ad {
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
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = stock
        )
        log.info("Creating new ad with title '{}' for user {}", title, userId)
        val savedDocument = try {
            adRepository.save(adDocument)
        } catch (e: IllegalStateException) {
            throw AdOperationException(e.message ?: "Failed to create ad.")
        }
        return savedDocument.toDomain()
    }

    /**
     * Updates an existing ad. Handles location and media update.
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

        // Media fields validation
        val newMediaPaths = updateData.mediaPaths ?: existingAdDoc.mediaPaths
        var newMainPhotoPath = updateData.mainPhotoPath ?: existingAdDoc.mainPhotoPath

        if (newMainPhotoPath != null && (newMediaPaths == null || !newMediaPaths.contains(newMainPhotoPath))) {
            log.warn("Update failed for ad {}: mainPhotoPath '{}' is not in mediaPaths list.", adId, newMainPhotoPath)
            throw AdOperationException("Main photo path must be one of the media paths.")
        }
        if ((newMediaPaths == null || newMediaPaths.isEmpty()) && newMainPhotoPath != null) {
            log.warn("Update failed for ad {}: mainPhotoPath is set but mediaPaths is empty. Clearing mainPhotoPath.", adId)
            newMainPhotoPath = null // Or throw error: "Cannot set main photo if media paths are empty."
        }

        val newPrice = updateData.price?.amount ?: existingAdDoc.price
        val newCurrency = updateData.price?.currency ?: existingAdDoc.currency

        val newStock = updateData.stock ?: existingAdDoc.stock
        try {
            adStockService.validateStockLevel(adObjectId, newStock)
        } catch (e: IllegalStateException) {
            throw AdOperationException(e.message ?: "Invalid stock level.")
        }

        val updatedAdDoc = existingAdDoc.copy(
            title = newTitle,
            description = updateData.description ?: existingAdDoc.description,
            price = newPrice,
            currency = newCurrency,
            cityId = newCityId ?: existingAdDoc.cityId,
            location = newGeoPoint ?: existingAdDoc.location,
            mediaPaths = newMediaPaths,
            mainPhotoPath = newMainPhotoPath,
            stock = newStock,
            updatedAtMicros = Instant.now().toMicros()
        )

        log.info("Updating ad {} for user {}", adId, userId)
        val savedDocument = try {
            adRepository.save(updatedAdDoc)
        } catch (e: IllegalStateException) {
            throw AdOperationException(e.message ?: "Failed to update ad.")
        }
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
     * Retrieves multiple ads by their IDs.
     */
    fun getAds(adIds: List<String>): List<Ad> {
        val objectIds = adIds.mapNotNull {
            try {
                ObjectId(it)
            } catch (e: IllegalArgumentException) {
                log.warn("Invalid ad ID format received: {}", it)
                null
            }
        }
        if (objectIds.isEmpty()) return emptyList()
        return adRepository.findAllByIds(objectIds).map { it.toDomain() }
    }

    fun getAds(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): Page<Ad> {
        val effectiveFilters = if (filters.createdBy != null) {
            filters
        } else {
            // Ensure only ACTIVE ads are fetched if not fetching by createdBy (my ads)
            filters // Status is already filtered in AdRepository.find for general queries
        }
        log.debug("Fetching ads with filters: {}, sorting: {}, page: {}", effectiveFilters, sorting, pageRequest)
        val pageOfAdDocuments = adRepository.find(effectiveFilters, sorting, pageRequest)
        return pageOfAdDocuments.map { it.toDomain() }
    }


    /**
     * Retrieves featured ads (currently most recent active ads).
     */
    fun getFeaturedAds(): Page<Ad> {
        val filters = AdFilters() // Status ACTIVE is applied in repository
        val sorting = AdSorting(by = By.CREATED_DATE, direction = Direction.DESC)
        // Consider if PAGE_SIZE from controller should be used or a specific one for featured
        val pageRequest = PageRequest.of(0, 30)

        log.debug("Fetching featured ads")
        val pageOfAdDocuments = adRepository.find(filters, sorting, pageRequest)
        return pageOfAdDocuments.map { it.toDomain() }
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
        val savedDocument = try {
            adRepository.save(activatedAdDoc)
        } catch (e: IllegalStateException) {
            throw AdOperationException(e.message ?: "Failed to activate ad.")
        }
        return savedDocument.toDomain()
    }

    /**
     * Deactivates an ad, changing its status to INACTIVE.
     */
    fun deactivateAd(adId: String, userId: String): Ad {
        val adObjectId = ObjectId(adId)
        val userObjectId = ObjectId(userId)
        val existingAdDoc = adRepository.findById(adObjectId)
            ?: throw AdNotFoundException(adId)

        if (existingAdDoc.userId != userObjectId) {
            log.warn("User {} attempted to deactivate ad {} owned by {}", userId, adId, existingAdDoc.userId)
            throw AdOperationException("User does not have permission to deactivate this ad.")
        }

        if (existingAdDoc.status != AdStatus.ACTIVE) {
            log.warn("Attempted to deactivate ad {} which is not in ACTIVE state (current: {})", adId, existingAdDoc.status)
            throw AdOperationException("Ad can only be deactivated when in ACTIVE state.")
        }

        val deactivatedAdDoc = existingAdDoc.copy(
            status = AdStatus.INACTIVE,
            updatedAtMicros = Instant.now().toMicros()
        )

        log.info("Deactivating ad {} for user {}", adId, userId)
        val savedDocument = adRepository.save(deactivatedAdDoc)
        return savedDocument.toDomain()
    }

    private fun checkForCompleteness(existingAd: AdDocument, adId: String) {
        val isComplete = existingAd.title.isNotBlank() &&
                !existingAd.description.isNullOrBlank() &&
                existingAd.price != null &&
                existingAd.currency != null
                && (existingAd.cityId != null || existingAd.location != null)
                && existingAd.stock > 0

        if (!isComplete) {
            log.warn(
                "Attempted to activate incomplete ad {}: title={}, desc={}, price={}, curr={}, location={}, stock={}",
                adId,
                existingAd.title.isNotBlank(),
                !existingAd.description.isNullOrBlank(),
                existingAd.price != null,
                existingAd.currency != null,
                existingAd.cityId != null || existingAd.location != null,
                existingAd.stock
            )
            throw AdOperationException("Ad cannot be activated until title, description, price, location and stock are all set. Stock must be greater than 0.")
        }
    }
}