package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdRepository
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdStockService(private val adRepository: AdRepository) {
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
        adRepository.decrementStockAndLockedStock(adId, quantity)
    }

    fun validateStockLevel(adId: ObjectId, newStock: Int) {
        val ad = adRepository.findById(adId) ?: return
        if (newStock < ad.lockedStock) {
            log.warn("Validation failed for ad {}: requested stock {} is lower than locked stock {}", adId, newStock, ad.lockedStock)
            throw IllegalStateException("Stock ($newStock) cannot be lower than locked stock (${ad.lockedStock}).")
        }
    }
}
