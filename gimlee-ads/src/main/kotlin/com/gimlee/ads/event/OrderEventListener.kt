package com.gimlee.ads.event

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.OrderEvent
import com.gimlee.ads.domain.model.OrderStatus
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class OrderEventListener(private val adRepository: AdRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onOrderEvent(event: OrderEvent) {
        log.info("Received OrderEvent for order ${event.orderId}, status ID: ${event.status}")
        
        when (event.status) {
            OrderStatus.CREATED.id -> {
                log.info("Incrementing locked stock for ad ${event.adId}")
                adRepository.incrementLockedStock(event.adId)
            }
            OrderStatus.COMPLETE.id -> {
                log.info("Completing sale for ad ${event.adId}")
                adRepository.completeSale(event.adId)
            }
            OrderStatus.FAILED_PAYMENT_TIMEOUT.id,
            OrderStatus.FAILED_PAYMENT_UNDERPAID.id,
            OrderStatus.CANCELLED.id -> {
                log.info("Decrementing locked stock for ad ${event.adId}")
                adRepository.decrementLockedStock(event.adId)
            }
        }
    }
}
