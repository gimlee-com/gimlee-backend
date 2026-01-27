package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.AdStatusChangedEvent
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class AdStockService(
    private val adRepository: AdRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun incrementLockedStock(adId: ObjectId, quantity: Int) {
        log.info("Incrementing locked stock for ad {} by {}", adId, quantity)
        adRepository.incrementLockedStock(adId, quantity)
    }

    fun decrementLockedStock(adId: ObjectId, quantity: Int) {
        log.info("Decrementing locked stock for ad {} by {}", adId, quantity)
        adRepository.decrementLockedStock(adId, quantity)
    }

    fun commitStock(adId: ObjectId, quantity: Int) {
        log.info("Committing stock for ad {} with quantity {}", adId, quantity)
        val oldAd = adRepository.decrementStockAndLockedStock(adId, quantity)
        if (oldAd != null && oldAd.status == AdStatus.ACTIVE && oldAd.stock - quantity <= 0) {
            log.info("Ad {} became INACTIVE due to stock depletion", adId)
            eventPublisher.publishEvent(AdStatusChangedEvent(
                adId = adId.toHexString(),
                oldStatus = AdStatus.ACTIVE.name,
                newStatus = AdStatus.INACTIVE.name,
                categoryIds = oldAd.categoryIds ?: emptyList()
            ))
        }
    }

    fun validateStockLevel(adId: ObjectId, newStock: Int) {
        val ad = adRepository.findById(adId) ?: return
        if (newStock < ad.lockedStock) {
            log.warn("Validation failed for ad {}: requested stock {} is lower than locked stock {}", adId, newStock, ad.lockedStock)
            throw AdService.AdOperationException(AdOutcome.STOCK_LOWER_THAN_LOCKED, newStock, ad.lockedStock)
        }
    }
}
