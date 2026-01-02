package com.gimlee.purchases.domain

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.events.PaymentEvent
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.persistence.PurchaseRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
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
        adId: ObjectId,
        amount: BigDecimal,
        currency: Currency
    ): Purchase {
        val ad = adService.getAd(adId.toHexString())
            ?: throw IllegalArgumentException("Ad not found: $adId")

        val adPrice = ad.price
        if (adPrice == null) {
            throw IllegalStateException("Ad $adId has no price set.")
        }

        if (adPrice.currency != currency || adPrice.amount.compareTo(amount) != 0) {
            log.warn("Purchase initialization rejected for ad {}: Price mismatch. Requested: {} {}, Actual: {} {}",
                adId, amount, currency, adPrice.amount, adPrice.currency)
            throw AdPriceMismatchException(adPrice)
        }

        val availableStock = ad.stock - ad.lockedStock
        if (availableStock <= 0) {
            throw IllegalStateException("Ad $adId has no available stock.")
        }

        return initPurchase(
            buyerId = buyerId,
            sellerId = ObjectId(ad.userId),
            adId = adId,
            amount = amount
        )
    }

    class AdPriceMismatchException(val currentPrice: CurrencyAmount) : RuntimeException("Price has changed.")

    fun initPurchase(
        buyerId: ObjectId,
        sellerId: ObjectId,
        adId: ObjectId,
        amount: BigDecimal
    ): Purchase {
        val purchaseId = ObjectId.get()
        val now = Instant.now()
        
        val purchase = Purchase(
            id = purchaseId,
            buyerId = buyerId,
            sellerId = sellerId,
            adId = adId,
            amount = amount,
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
            amount = amount,
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
        val event = PurchaseEvent(
            purchaseId = purchase.id,
            adId = purchase.adId,
            buyerId = purchase.buyerId,
            sellerId = purchase.sellerId,
            status = purchase.status.id,
            amount = purchase.amount,
            timestamp = Instant.now()
        )
        eventPublisher.publishEvent(event)
    }
    
    fun getPurchase(purchaseId: ObjectId): Purchase? = purchaseRepository.findById(purchaseId)
}
