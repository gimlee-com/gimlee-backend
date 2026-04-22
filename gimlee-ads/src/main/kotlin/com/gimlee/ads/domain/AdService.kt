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
import com.gimlee.ads.persistence.AdRepository.AdConcurrentModificationException
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.model.Range
import com.gimlee.common.toMicros
import com.gimlee.events.AdStatusChangedEvent
import com.gimlee.events.AdPriceChangedEvent
import com.gimlee.events.AdRestockedEvent
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

    /** Thrown when ad completeness validation fails with field-level details. */
    class AdValidationException(
        val outcome: AdOutcome,
        val fieldErrors: List<FieldError>
    ) : RuntimeException() {
        data class FieldError(val field: String, val messageKey: String)
    }

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
            fixedPrices = emptyMap(),
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
        validateConflictingFields(updateData, existingAdDoc)

        val updatedFields = resolveUpdatedFields(existingAdDoc, updateData)
        
        validateBusinessRules(existingAdDoc, updatedFields, userObjectId)

        val updatedAdDoc = applyUpdates(existingAdDoc, updatedFields)

        if (existingAdDoc.status == AdStatus.ACTIVE) {
            checkForCompleteness(updatedAdDoc, adId, AdOutcome.ACTIVE_AD_INCOMPLETE_UPDATE)
        }

        log.info("Updating ad {} for user {}", adId, userId)
        val savedDocument = try {
            adRepository.save(updatedAdDoc)
        } catch (e: AdConcurrentModificationException) {
            throw AdOperationException(AdOutcome.CONCURRENT_MODIFICATION)
        } catch (e: IllegalStateException) {
            throw AdOperationException(AdOutcome.UPDATE_FAILED)
        }

        publishAdChangeEvents(existingAdDoc, updatedFields, adId, userId)

        return savedDocument.toDomain()
    }

    private fun validateAdOwnershipAndStatus(ad: AdDocument, userId: ObjectId) {
        if (ad.userId != userId) {
            log.warn("User {} attempted to update ad {} owned by {}", userId, ad.id, ad.userId)
            throw AdOperationException(AdOutcome.NOT_AD_OWNER)
        }

        if (ad.status == AdStatus.DELETED) {
            log.warn("Attempted to update ad {} which is in DELETED state", ad.id)
            throw AdOperationException(AdOutcome.INVALID_AD_STATUS)
        }
    }

    private fun validateUpdateData(updateData: UpdateAdRequest) {
        if (updateData.title != null && updateData.title.isBlank()) {
            throw AdOperationException(AdOutcome.TITLE_MANDATORY)
        }
    }

    private fun validateConflictingFields(update: UpdateAdRequest, existing: AdDocument) {
        val effectiveMode = update.pricingMode ?: existing.pricingMode
        when (effectiveMode) {
            PricingMode.FIXED_CRYPTO -> {
                if (update.price != null) {
                    throw AdOperationException(AdOutcome.FIXED_CRYPTO_CONFLICTING_PRICE)
                }
                if (update.settlementCurrencies != null) {
                    throw AdOperationException(AdOutcome.FIXED_CRYPTO_CONFLICTING_SETTLEMENT)
                }
            }
            PricingMode.PEGGED -> {
                if (update.fixedPrices != null) {
                    throw AdOperationException(AdOutcome.PEGGED_CONFLICTING_FIXED_PRICES)
                }
            }
        }
    }

    private data class AdUpdateFields(
        val title: String,
        val description: String?,
        val pricingMode: PricingMode,
        val price: BigDecimal?,
        val currency: Currency?,
        val fixedPrices: Map<Currency, BigDecimal>,
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

        val pricingMode = update.pricingMode ?: existing.pricingMode
        val fixedPrices = if (pricingMode == PricingMode.FIXED_CRYPTO) {
            update.fixedPrices ?: existing.fixedPrices
        } else {
            emptyMap()
        }

        val settlementCurrencies = if (pricingMode == PricingMode.FIXED_CRYPTO) {
            fixedPrices.keys
        } else {
            update.settlementCurrencies ?: existing.settlementCurrencies
        }

        val price = if (pricingMode == PricingMode.PEGGED) {
            update.price?.amount ?: existing.price
        } else {
            null
        }
        val currency = if (pricingMode == PricingMode.PEGGED) {
            update.price?.currency ?: existing.currency
        } else {
            null
        }

        return AdUpdateFields(
            title = update.title ?: existing.title,
            description = update.description ?: existing.description,
            pricingMode = pricingMode,
            price = price,
            currency = currency,
            fixedPrices = fixedPrices,
            settlementCurrencies = settlementCurrencies,
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
        // Fixed crypto validation
        if (fields.pricingMode == PricingMode.FIXED_CRYPTO) {
            fields.fixedPrices.keys.forEach { currency ->
                if (!currency.isSettlement) {
                    throw AdOperationException(AdOutcome.FIXED_CRYPTO_INVALID_CURRENCY, currency.name)
                }
            }
            fields.fixedPrices.forEach { (currency, amount) ->
                if (amount <= BigDecimal.ZERO) {
                    throw AdOperationException(AdOutcome.FIXED_CRYPTO_INVALID_PRICE, currency.name)
                }
            }
        }

        // Settlement currency validation (for PEGGED mode)
        if (fields.pricingMode == PricingMode.PEGGED) {
            fields.settlementCurrencies.forEach { sc ->
                if (!sc.isSettlement) {
                    throw AdOperationException(AdOutcome.CURRENCY_NOT_ALLOWED, sc.name)
                }
            }
        }

        // Price change validation (for PEGGED mode)
        if (fields.pricingMode == PricingMode.PEGGED && fields.price != null && fields.currency != null) {
            val oldPriceValue = existing.price
            val oldCurrencyValue = existing.currency
            val oldPrice = if (oldPriceValue != null && oldCurrencyValue != null) CurrencyAmount(oldPriceValue, oldCurrencyValue) else null
            
            adPriceValidator.validatePrice(CurrencyAmount(fields.price, fields.currency), oldPrice)
        }

        // Price limit validation for each fixed price
        if (fields.pricingMode == PricingMode.FIXED_CRYPTO) {
            fields.fixedPrices.forEach { (currency, amount) ->
                val oldAmount = existing.fixedPrices[currency]
                val oldPrice = oldAmount?.let { CurrencyAmount(it, currency) }
                adPriceValidator.validatePrice(CurrencyAmount(amount, currency), oldPrice)
            }
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
            fixedPrices = fields.fixedPrices,
            settlementCurrencies = fields.settlementCurrencies,
            cityId = fields.cityId,
            categoryIds = fields.categoryIds,
            location = fields.location,
            mediaPaths = fields.mediaPaths,
            mainPhotoPath = fields.mainPhotoPath,
            stock = fields.stock,
            lockedStock = existing.lockedStock,
            volatilityProtection = fields.volatilityProtection,
            updatedAtMicros = Instant.now().toMicros()
        )
    }

    private fun publishAdChangeEvents(existing: AdDocument, updated: AdUpdateFields, adId: String, userId: String) {
        val priceChanged = when (updated.pricingMode) {
            PricingMode.FIXED_CRYPTO -> existing.fixedPrices != updated.fixedPrices
            PricingMode.PEGGED -> {
                val oldPrice = existing.price
                val oldCurrency = existing.currency
                val newPrice = updated.price
                val newCurrency = updated.currency
                newPrice != null && newCurrency != null && oldPrice != null && oldCurrency != null &&
                    (newPrice.compareTo(oldPrice) != 0 || newCurrency != oldCurrency)
            }
        }

        if (priceChanged) {
            val oldPriceStr = if (updated.pricingMode == PricingMode.FIXED_CRYPTO) {
                existing.fixedPrices.entries.joinToString(", ") { "${it.value} ${it.key}" }
            } else {
                "${existing.price} ${existing.currency?.name}"
            }
            val newPriceStr = if (updated.pricingMode == PricingMode.FIXED_CRYPTO) {
                updated.fixedPrices.entries.joinToString(", ") { "${it.value} ${it.key}" }
            } else {
                "${updated.price} ${updated.currency?.name}"
            }
            eventPublisher.publishEvent(AdPriceChangedEvent(
                adId = adId,
                sellerId = userId,
                adTitle = updated.title,
                oldPrice = oldPriceStr,
                newPrice = newPriceStr,
                currency = updated.currency?.name ?: updated.fixedPrices.keys.firstOrNull()?.name ?: ""
            ))
        }

        val oldStock = existing.stock
        val newStock = updated.stock
        if (oldStock <= 0 && newStock > 0) {
            eventPublisher.publishEvent(AdRestockedEvent(
                adId = adId,
                sellerId = userId,
                adTitle = updated.title,
                newStock = newStock
            ))
        }
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

    private fun checkForCompleteness(existingAd: AdDocument, adId: String, outcome: AdOutcome = AdOutcome.INCOMPLETE_AD_DATA) {
        val fieldErrors = mutableListOf<AdValidationException.FieldError>()

        if (existingAd.title.isBlank()) {
            fieldErrors += AdValidationException.FieldError("title", "validation.ad.title-required")
        }
        if (existingAd.description.isNullOrBlank()) {
            fieldErrors += AdValidationException.FieldError("description", "validation.ad.description-required")
        }

        when (existingAd.pricingMode) {
            PricingMode.FIXED_CRYPTO -> {
                if (existingAd.fixedPrices.isEmpty()) {
                    fieldErrors += AdValidationException.FieldError("fixedPrices", "validation.ad.fixed-prices-required")
                }
            }
            PricingMode.PEGGED -> {
                if (existingAd.price == null) {
                    fieldErrors += AdValidationException.FieldError("price", "validation.ad.price-required")
                }
                if (existingAd.currency == null) {
                    fieldErrors += AdValidationException.FieldError("priceCurrency", "validation.ad.price-currency-required")
                }
                val hasValidSettlement = existingAd.settlementCurrencies.isNotEmpty() &&
                        existingAd.settlementCurrencies.all { it.isSettlement }
                if (!hasValidSettlement) {
                    fieldErrors += AdValidationException.FieldError("settlementCurrencies", "validation.ad.settlement-currencies-required")
                }
            }
        }

        if (existingAd.cityId == null || existingAd.location == null) {
            fieldErrors += AdValidationException.FieldError("location", "validation.ad.location-required")
        }
        if (existingAd.stock <= 0) {
            fieldErrors += AdValidationException.FieldError("stock", "validation.ad.stock-required")
        }

        if (fieldErrors.isNotEmpty()) {
            log.warn(
                "Completeness check failed for ad {}: {}",
                adId,
                fieldErrors.joinToString(", ") { it.field }
            )
            throw AdValidationException(outcome, fieldErrors)
        }
    }

    private fun resolveCategoryPath(categoryId: Int?): List<Int>? {
        if (categoryId == null) return null

        if (categoryService.isHidden(categoryId)) {
            throw AdOperationException(AdOutcome.CATEGORY_HIDDEN)
        }
        
        if (!categoryService.isLeaf(categoryId)) {
            throw AdOperationException(AdOutcome.CATEGORY_NOT_LEAF)
        }
        return categoryService.resolveCategoryPathIds(categoryId)
    }
}