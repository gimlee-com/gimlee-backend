package com.gimlee.ads.event

import com.gimlee.ads.domain.AdStockService
import com.gimlee.events.PurchaseEvent
import com.gimlee.ads.domain.model.PurchaseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PurchaseEventListener(private val adStockService: AdStockService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        log.info("Received PurchaseEvent for purchase ${event.purchaseId}, status ID: ${event.status}")
        
        event.items.forEach { item ->
            when (event.status) {
                PurchaseStatus.CREATED.id -> {
                    adStockService.incrementLockedStock(item.adId, item.quantity)
                }
                PurchaseStatus.COMPLETE.id -> {
                    adStockService.commitStock(item.adId, item.quantity)
                }
                PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
                PurchaseStatus.FAILED_PAYMENT_UNDERPAID.id,
                PurchaseStatus.CANCELLED.id -> {
                    adStockService.decrementLockedStock(item.adId, item.quantity)
                }
            }
        }
    }
}
