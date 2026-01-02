package com.gimlee.ads.event

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.PurchaseEvent
import com.gimlee.ads.domain.model.PurchaseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PurchaseEventListener(private val adRepository: AdRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        log.info("Received PurchaseEvent for purchase ${event.purchaseId}, status ID: ${event.status}")
        
        event.items.forEach { item ->
            when (event.status) {
                PurchaseStatus.CREATED.id -> {
                    log.info("Incrementing locked stock for ad ${item.adId} by ${item.quantity}")
                    adRepository.incrementLockedStock(item.adId, item.quantity)
                }
                PurchaseStatus.COMPLETE.id -> {
                    log.info("Completing sale for ad ${item.adId} with quantity ${item.quantity}")
                    adRepository.decrementStockAndLockedStock(item.adId, item.quantity)
                }
                PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
                PurchaseStatus.FAILED_PAYMENT_UNDERPAID.id,
                PurchaseStatus.CANCELLED.id -> {
                    log.info("Decrementing locked stock for ad ${item.adId} by ${item.quantity}")
                    adRepository.decrementLockedStock(item.adId, item.quantity)
                }
            }
        }
    }
}
