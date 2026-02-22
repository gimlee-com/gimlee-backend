package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.model.Range
import com.gimlee.common.toMicros
import com.gimlee.events.AdStatusChangedEvent
import com.gimlee.location.cities.data.cityDataById
import com.gimlee.payments.domain.service.CurrencyConverterService
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant


@Service
class AdService(
    private val adRepository: AdRepository,
    private val adStockService: AdStockService,
    private val categoryService: CategoryService,
    private val currencyConverterService: CurrencyConverterService,
    private val adCurrencyValidator: AdCurrencyValidator,
    private val adCurrencyService: AdCurrencyService,
    private val adPriceValidator: AdPriceValidator,
    private val userRoleRepository: UserRoleRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Thrown when an ad operation cannot be performed due to business rules. */
    class AdOperationException(val outcome: AdOutcome, vararg val args: Any) : RuntimeException()

    /** Thrown when a user lacks the required role for a currency. */
    class AdCurrencyRoleException(val outcome: AdOutcome) : RuntimeException()

    /** Thrown when an ad is not found. */
    class AdNotFoundException(adId: String) : RuntimeException("Ad not found with ID: $adId")

    /**
     * Initiates a new ad with a title in the INACTIVE state. Location and other details are set via updateAd.
     */
    fun createAd(userId: String, title: String, categoryId: Int? = null, stock: Int = 0): Ad {
        val nowMicros = Instant.now().toMicros()
        val adDocument = AdDocument(
            id = ObjectId(),
            userId = ObjectId(userId),
            title = title,
            description = null,
            pricingMode = PricingMode.FIXED_CRYPTO,
            price = null,
            currency = null,
            settlementCurrencies = emptySet(),
            status = AdStatus.INACTIVE,
            createdAtMicros = nowMicros,
            updatedAtMicros = nowMicros,
            cityId = null,
            categoryIds = resolveCategoryPath(categoryId),
            location = null,
            mediaPaths = emptyList(),
            mainPhotoPath = null,
            stock = stock
        )
        log.info("Creating new ad with title '{}' for user {}", title, userId)
        val savedDocument = try {
            adRepository.save(adDocument)
        } catch (e: IllegalStateException) {
            throw AdOperationException(AdOutcome.CREATION_FAILED)
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

        validateAdOwnershipAndStatus(existingAdDoc, userObjectId)
        validateUpdateData(updateData)

        val updatedFields = resolveUpdatedFields(existingAdDoc, updateData)
        
        validateBusinessRules(existingAdDoc, updatedFields, userObjectId)

        val updatedAdDoc = applyUpdates(existingAdDoc, updatedFields)

        log.info("Updating ad {} for user {}", adId, userId)
        val savedDocument = try {
            adRepository.save(updatedAdDoc)
        } catch (e: IllegalStateException) {
            throw AdOperationException(AdOutcome.UPDATE_FAILED)
        }
        return savedDocument.toDomain()
    }

    private fun validateAdOwnershipAndStatus(ad: AdDocument, userId: ObjectId) {
        if (ad.userId != userId) {
            log.warn("User {} attempted to update ad {} owned by {}", userId, ad.id, ad.userId)
            throw AdOperationException(AdOutcome.NOT_AD_OWNER)
        }

        if (ad.status != AdStatus.INACTIVE) {
            log.warn("Attempted to update ad {} which is not in INACTIVE state (current: {})", ad.id, ad.status)
            throw AdOperationException(AdOutcome.INVALID_AD_STATUS)
        }
    }

    private fun validateUpdateData(updateData: UpdateAdRequest) {
        if (updateData.title != null && updateData.title.isBlank()) {
            throw AdOperationException(AdOutcome.TITLE_MANDATORY)
        }
    }

    private data class AdUpdateFields(
        val title: String,
        val description: String?,
        val pricingMode: PricingMode,
        val price: BigDecimal?,
        val currency: Currency?,
        val settlementCurrencies: Set<Currency>,
        val cityId: String?,
        val location: GeoJsonPoint?,
        val mediaPaths: List<String>,
        val mainPhotoPath: String?,
        val stock: Int,
        val volatilityProtection: Boolean,
        val categoryIds: List<Int>?
    )

    private fun resolveUpdatedFields(existing: AdDocument, update: UpdateAdRequest): AdUpdateFields {
        val newCityId = update.location?.cityId ?: existing.cityId
        var newGeoPoint = update.location?.let { GeoJsonPoint(it.point[0], it.point[1]) } ?: existing.location

        if (newCityId != null && newGeoPoint == null) {
            newGeoPoint = cityDataById[newCityId]?.let { GeoJsonPoint(it.lon, it.lat) }
        }

        val newMediaPaths = update.mediaPaths ?: existing.mediaPaths ?: emptyList()
        var newMainPhotoPath = update.mainPhotoPath ?: existing.mainPhotoPath

        // Media validation logic
        if (newMainPhotoPath != null && !newMediaPaths.contains(newMainPhotoPath)) {
            log.warn("Update failed for ad {}: mainPhotoPath '{}' is not in mediaPaths list.", existing.id, newMainPhotoPath)
            throw AdOperationException(AdOutcome.INVALID_MAIN_PHOTO)
        }
        if (newMediaPaths.isEmpty() && newMainPhotoPath != null) {
            newMainPhotoPath = null
        }

        return AdUpdateFields(
            title = update.title ?: existing.title,
            description = update.description ?: existing.description,
            pricingMode = update.pricingMode ?: existing.pricingMode,
            price = update.price?.amount ?: existing.price,
            currency = update.price?.currency ?: existing.currency,
            settlementCurrencies = update.settlementCurrencies ?: existing.settlementCurrencies,
            cityId = newCityId,
            location = newGeoPoint,
            mediaPaths = newMediaPaths,
            mainPhotoPath = newMainPhotoPath,
            stock = update.stock ?: existing.stock,
            volatilityProtection = update.volatilityProtection ?: existing.volatilityProtection,
            categoryIds = if (update.categoryId != null) resolveCategoryPath(update.categoryId) else existing.categoryIds
        )
    }

    private fun validateBusinessRules(existing: AdDocument, fields: AdUpdateFields, userId: ObjectId) {
        // Volatility protection validation
        if (fields.volatilityProtection && fields.pricingMode != PricingMode.PEGGED) {
            throw AdOperationException(AdOutcome.VOLATILITY_PROTECTION_REQUIRES_PEGGED)
        }

        // Fixed crypto validation
        if (fields.pricingMode == PricingMode.FIXED_CRYPTO && fields.currency != null) {
            if (!fields.currency.isSettlement) {
                throw AdOperationException(AdOutcome.FIXED_CRYPTO_REQUIRES_SETTLEMENT_CURRENCY)
            }
            if (fields.settlementCurrencies.isNotEmpty() && fields.currency !in fields.settlementCurrencies) {
                throw AdOperationException(AdOutcome.FIXED_CRYPTO_PRICE_CURRENCY_NOT_IN_SETTLEMENT)
            }
        }

        // Settlement currency validation
        fields.settlementCurrencies.forEach { sc ->
            if (!sc.isSettlement) {
                throw AdOperationException(AdOutcome.CURRENCY_NOT_ALLOWED, sc.name)
            }
        }

        // Price change validation
        if (fields.price != null && fields.currency != null) {
            val oldPriceValue = existing.price
            val oldCurrencyValue = existing.currency
            val oldPrice = if (oldPriceValue != null && oldCurrencyValue != null) CurrencyAmount(oldPriceValue, oldCurrencyValue) else null
            
            adPriceValidator.validatePrice(CurrencyAmount(fields.price, fields.currency), oldPrice)
        }

        // User role validation
        validateUserRoles(userId, fields.settlementCurrencies, fields.currency)

        // Stock validation
        adStockService.validateStockLevel(existing.id, fields.stock)
    }

    private fun validateUserRoles(userId: ObjectId, settlementCurrencies: Set<Currency>, priceCurrency: Currency?) {
        val roles = userRoleRepository.getAll(userId)
        
        if (settlementCurrencies.isNotEmpty()) {
            settlementCurrencies.forEach { sc ->
                adCurrencyValidator.validateUserCanListInCurrency(roles, sc)
            }
        } else if (priceCurrency != null && priceCurrency.isSettlement) {
            adCurrencyValidator.validateUserCanListInCurrency(roles, priceCurrency)
        }
    }

    private fun applyUpdates(existing: AdDocument, fields: AdUpdateFields): AdDocument {
        return existing.copy(
            title = fields.title,
            description = fields.description,
            pricingMode = fields.pricingMode,
            price = fields.price,
            currency = fields.currency,
            settlementCurrencies = fields.settlementCurrencies,
            cityId = fields.cityId,
            categoryIds = fields.categoryIds,
            location = fields.location,
            mediaPaths = fields.mediaPaths,
            mainPhotoPath = fields.mainPhotoPath,
            stock = fields.stock,
            lockedStock = existing.lockedStock, // Locked stock is not updated via this method
            volatilityProtection = fields.volatilityProtection,
            updatedAtMicros = Instant.now().toMicros()
        )
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

    /**
     * Retrieves the list of settlement currencies a user is allowed to list ads in.
     */
    fun getAllowedCurrencies(userId: String): List<Currency> {
        val roles = userRoleRepository.getAll(ObjectId(userId))
        return adCurrencyService.getAllowedCurrencies(roles)
    }

    fun getAds(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): Page<Ad> {
        log.debug("Fetching ads with filters: {}, sorting: {}, page: {}", filters, sorting, pageRequest)
        
        val effectiveFilters = if (filters.priceRange != null && filters.preferredCurrency != null && filters.priceRanges == null) {
            val translatedRanges = translatePriceRange(filters.priceRange, filters.preferredCurrency)
            filters.copy(priceRanges = translatedRanges)
        } else {
            filters
        }
        
        val pageOfAdDocuments = adRepository.find(effectiveFilters, sorting, pageRequest)
        return pageOfAdDocuments.map { it.toDomain() }
    }

    private fun translatePriceRange(range: Range<BigDecimal>, preferredCurrency: Currency): Map<Currency, Range<BigDecimal>> {
        val result = mutableMapOf<Currency, Range<BigDecimal>>()

        Currency.entries.forEach { targetCurrency ->
            try {
                val from = range.from?.let { currencyConverterService.convert(it, preferredCurrency, targetCurrency).targetAmount }
                val to = range.to?.let { currencyConverterService.convert(it, preferredCurrency, targetCurrency).targetAmount }

                if (from != null || to != null) {
                    result[targetCurrency] = Range(from, to)
                }
            } catch (e: Exception) {
                log.warn("Could not translate price range from {} to {}: {}", preferredCurrency, targetCurrency, e.message)
            }
        }

        return result
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
            throw AdOperationException(AdOutcome.NOT_AD_OWNER)
        }

        if (existingAdDoc.status != AdStatus.INACTIVE) {
            log.warn("Attempted to activate ad {} which is not in INACTIVE state (current: {})", adId, existingAdDoc.status)
            throw AdOperationException(AdOutcome.INVALID_AD_STATUS)
        }

        if (existingAdDoc.settlementCurrencies.isNotEmpty()) {
            val roles = userRoleRepository.getAll(userObjectId)
            existingAdDoc.settlementCurrencies.forEach { sc ->
                adCurrencyValidator.validateUserCanListInCurrency(roles, sc)
            }
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
            throw AdOperationException(AdOutcome.ACTIVATION_FAILED)
        }

        eventPublisher.publishEvent(AdStatusChangedEvent(
            adId = adId,
            oldStatus = existingAdDoc.status.name,
            newStatus = activatedAdDoc.status.name,
            categoryIds = activatedAdDoc.categoryIds ?: emptyList()
        ))

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
            throw AdOperationException(AdOutcome.NOT_AD_OWNER)
        }

        if (existingAdDoc.status != AdStatus.ACTIVE) {
            log.warn("Attempted to deactivate ad {} which is not in ACTIVE state (current: {})", adId, existingAdDoc.status)
            throw AdOperationException(AdOutcome.INVALID_AD_STATUS)
        }

        val deactivatedAdDoc = existingAdDoc.copy(
            status = AdStatus.INACTIVE,
            updatedAtMicros = Instant.now().toMicros()
        )

        log.info("Deactivating ad {} for user {}", adId, userId)
        val savedDocument = adRepository.save(deactivatedAdDoc)

        eventPublisher.publishEvent(AdStatusChangedEvent(
            adId = adId,
            oldStatus = existingAdDoc.status.name,
            newStatus = deactivatedAdDoc.status.name,
            categoryIds = deactivatedAdDoc.categoryIds ?: emptyList()
        ))

        return savedDocument.toDomain()
    }

    private fun checkForCompleteness(existingAd: AdDocument, adId: String) {
        val hasValidSettlement = existingAd.settlementCurrencies.isNotEmpty() &&
                existingAd.settlementCurrencies.all { it.isSettlement }

        val isComplete = existingAd.title.isNotBlank() &&
                !existingAd.description.isNullOrBlank() &&
                existingAd.price != null &&
                existingAd.currency != null &&
                hasValidSettlement &&
                existingAd.cityId != null &&
                existingAd.location != null &&
                existingAd.stock > 0

        if (!isComplete) {
            log.warn(
                "Attempted to activate incomplete ad {}: title={}, desc={}, price={}, curr={}, settlement={}, cityId={}, location={}, stock={}",
                adId,
                existingAd.title.isNotBlank(),
                !existingAd.description.isNullOrBlank(),
                existingAd.price != null,
                existingAd.currency != null,
                hasValidSettlement,
                existingAd.cityId != null,
                existingAd.location != null,
                existingAd.stock
            )
            throw AdOperationException(AdOutcome.INCOMPLETE_AD_DATA)
        }
    }

    private fun resolveCategoryPath(categoryId: Int?): List<Int>? {
        if (categoryId == null) return null
        
        if (!categoryService.isLeaf(categoryId)) {
            throw AdOperationException(AdOutcome.CATEGORY_NOT_LEAF)
        }
        return categoryService.resolveCategoryPathIds(categoryId)
    }
}