package com.gimlee.purchases.domain

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.events.PaymentEvent
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseItem
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.PurchaseRepository
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val paymentService: PaymentService,
    private val adService: AdService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun purchase(
        buyerId: ObjectId,
        items: List<com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto>,
        currency: Currency
    ): Purchase {
        val purchasedItemDetails = items.map { itemRequest ->
            val ad = adService.getAd(itemRequest.adId)
                ?: throw IllegalArgumentException("Ad not found: ${itemRequest.adId}")

            val adPrice = ad.price
                ?: throw IllegalStateException("Ad ${itemRequest.adId} has no price set.")

            if (adPrice.currency != currency || adPrice.amount.compareTo(itemRequest.unitPrice) != 0) {
                log.warn("Purchase initialization rejected for ad {}: Price mismatch. Requested: {} {}, Actual: {} {}",
                    itemRequest.adId, itemRequest.unitPrice, currency, adPrice.amount, adPrice.currency)
                throw AdPriceMismatchException(adPrice)
            }

            val availableStock = ad.stock - ad.lockedStock
            if (availableStock < itemRequest.quantity) {
                throw IllegalStateException("Ad ${itemRequest.adId} has insufficient stock. Requested: ${itemRequest.quantity}, Available: $availableStock")
            }

            PurchasedItemDetails(
                adId = ObjectId(ad.id),
                quantity = itemRequest.quantity,
                unitPrice = adPrice.amount,
                currency = adPrice.currency,
                sellerId = ObjectId(ad.userId) // Helper to collect sellerId
            )
        }

        val sellerIds = purchasedItemDetails.map { it.sellerId }.toSet()
        if (sellerIds.size > 1) {
            throw IllegalArgumentException("All items in a purchase must belong to the same seller.")
        }
        val sellerId = sellerIds.first()

        val totalAmount = purchasedItemDetails.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }

        return initPurchase(
            buyerId = buyerId,
            sellerId = sellerId,
            items = purchasedItemDetails.map {
                PurchaseItem(
                    adId = it.adId,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    currency = it.currency
                )
            },
            totalAmount = totalAmount
        )
    }

    private data class PurchasedItemDetails(
        val adId: ObjectId,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val currency: Currency,
        val sellerId: ObjectId
    )

    class AdPriceMismatchException(val currentPrice: CurrencyAmount) : RuntimeException("Price has changed.")

    fun initPurchase(
        buyerId: ObjectId,
        sellerId: ObjectId,
        items: List<PurchaseItem>,
        totalAmount: BigDecimal
    ): Purchase {
        val purchaseId = ObjectId.get()
        val now = Instant.now()
        
        val purchase = Purchase(
            id = purchaseId,
            buyerId = buyerId,
            sellerId = sellerId,
            items = items,
            totalAmount = totalAmount,
            status = PurchaseStatus.CREATED,
            createdAt = now
        )
        
        purchaseRepository.save(purchase)
        publishPurchaseEvent(purchase)

        // Initialize payment
        paymentService.initPayment(
            purchaseId = purchaseId,
            buyerId = buyerId,
            sellerId = sellerId,
            amount = totalAmount,
            paymentMethod = PaymentMethod.PIRATE_CHAIN
        )

        // Update status to AWAITING_PAYMENT
        val activePurchase = purchase.copy(status = PurchaseStatus.AWAITING_PAYMENT)
        val savedPurchase = purchaseRepository.save(activePurchase)
        publishPurchaseEvent(savedPurchase)

        return savedPurchase
    }
    
    @EventListener
    fun onPaymentEvent(event: PaymentEvent) {
        val purchase = purchaseRepository.findById(event.purchaseId) ?: return
        
        val newStatus = when (event.status) {
            PaymentStatus.COMPLETE.id -> PurchaseStatus.COMPLETE
            PaymentStatus.COMPLETE_UNDERPAID.id -> PurchaseStatus.FAILED_PAYMENT_UNDERPAID
            PaymentStatus.FAILED_SOFT_TIMEOUT.id -> PurchaseStatus.FAILED_PAYMENT_TIMEOUT
            PaymentStatus.FAILED_HARD_TIMEOUT.id -> PurchaseStatus.FAILED_PAYMENT_TIMEOUT
            PaymentStatus.CANCELLED.id -> PurchaseStatus.CANCELLED
            else -> null
        }

        if (newStatus != null && purchase.status != newStatus) {
            log.info("Updating purchase ${purchase.id} status to $newStatus based on payment event.")
            val updatedPurchase = purchase.copy(status = newStatus)
            purchaseRepository.save(updatedPurchase)
            publishPurchaseEvent(updatedPurchase)
        }
    }

    private fun publishPurchaseEvent(purchase: Purchase) {
        val event = com.gimlee.events.PurchaseEvent(
            purchaseId = purchase.id,
            items = purchase.items.map { com.gimlee.events.PurchaseEventItem(it.adId, it.quantity) },
            buyerId = purchase.buyerId,
            sellerId = purchase.sellerId,
            status = purchase.status.id,
            totalAmount = purchase.totalAmount,
            timestamp = Instant.now()
        )
        eventPublisher.publishEvent(event)
    }
    
    fun getPurchase(purchaseId: ObjectId): Purchase? = purchaseRepository.findById(purchaseId)
}
