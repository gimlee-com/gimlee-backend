package com.gimlee.purchases.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.events.PaymentEvent
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseItem
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import com.gimlee.purchases.persistence.PurchaseRepository
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
        items: List<PurchaseItemRequestDto>,
        currency: Currency
    ): Purchase {
        val ads = adService.getAds(items.map { it.adId }).associateBy { it.id }

        validateAdsExist(items, ads)
        validateAdsAreActive(ads)
        validatePrices(items, ads, currency)

        val purchasedItemDetails = collectPurchasedItemDetails(items, ads)
        val sellerId = getAndValidateSingleSeller(purchasedItemDetails)

        if (buyerId == sellerId) {
            throw IllegalArgumentException("Cannot purchase from self.")
        }

        val totalAmount = calculateTotalAmount(purchasedItemDetails)

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
            totalAmount = totalAmount,
            paymentMethod = getPaymentMethod(currency)
        )
    }

    private fun getPaymentMethod(currency: Currency): PaymentMethod = when (currency) {
        Currency.ARRR -> PaymentMethod.PIRATE_CHAIN
        Currency.YEC -> PaymentMethod.YCASH
    }

    private fun validateAdsExist(items: List<PurchaseItemRequestDto>, ads: Map<String, Ad>) {
        val missingAdIds = items.map { it.adId }.filter { !ads.containsKey(it) }.distinct()
        if (missingAdIds.isNotEmpty()) {
            throw IllegalArgumentException("Ads not found: ${missingAdIds.joinToString()}")
        }
    }

    private fun validateAdsAreActive(ads: Map<String, Ad>) {
        val inactiveAdIds = ads.values.filter { it.status != AdStatus.ACTIVE }.map { it.id }
        if (inactiveAdIds.isNotEmpty()) {
            throw IllegalStateException("One or more ads are not active: ${inactiveAdIds.joinToString()}")
        }
    }

    private fun validatePrices(items: List<PurchaseItemRequestDto>, ads: Map<String, Ad>, currency: Currency) {
        val anyPriceMismatch = items.any { itemRequest ->
            val ad = ads[itemRequest.adId]!!
            val adPrice = ad.price ?: throw IllegalStateException("Ad ${itemRequest.adId} has no price set.")
            adPrice.currency != currency || adPrice.amount.compareTo(itemRequest.unitPrice) != 0
        }

        if (anyPriceMismatch) {
            val currentPrices = items.associate { itemRequest ->
                val ad = ads[itemRequest.adId]!!
                itemRequest.adId to ad.price!!
            }
            log.warn("Purchase initialization rejected: Price mismatch detected for one or more items.")
            throw AdPriceMismatchException(currentPrices)
        }
    }

    private fun collectPurchasedItemDetails(
        items: List<PurchaseItemRequestDto>,
        ads: Map<String, Ad>
    ): List<PurchasedItemDetails> {
        return items.map { itemRequest ->
            val ad = ads[itemRequest.adId]!!
            val adPrice = ad.price!!

            val availableStock = ad.stock - ad.lockedStock
            if (availableStock < itemRequest.quantity) {
                throw IllegalStateException("Ad ${itemRequest.adId} has insufficient stock. Requested: ${itemRequest.quantity}, Available: $availableStock")
            }

            PurchasedItemDetails(
                adId = ObjectId(ad.id),
                quantity = itemRequest.quantity,
                unitPrice = adPrice.amount,
                currency = adPrice.currency,
                sellerId = ObjectId(ad.userId)
            )
        }
    }

    private fun getAndValidateSingleSeller(purchasedItemDetails: List<PurchasedItemDetails>): ObjectId {
        val sellerIds = purchasedItemDetails.map { it.sellerId }.toSet()
        if (sellerIds.size > 1) {
            throw IllegalArgumentException("All items in a purchase must belong to the same seller.")
        }
        return sellerIds.first()
    }

    private fun calculateTotalAmount(purchasedItemDetails: List<PurchasedItemDetails>): BigDecimal {
        return purchasedItemDetails.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }
    }

    private data class PurchasedItemDetails(
        val adId: ObjectId,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val currency: Currency,
        val sellerId: ObjectId
    )

    class AdPriceMismatchException(val currentPrices: Map<String, CurrencyAmount>) : RuntimeException("Price has changed for one or more items.")

    fun initPurchase(
        buyerId: ObjectId,
        sellerId: ObjectId,
        items: List<PurchaseItem>,
        totalAmount: BigDecimal,
        paymentMethod: PaymentMethod
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
            paymentMethod = paymentMethod
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

    fun cancelPurchase(buyerId: ObjectId, purchaseId: ObjectId) {
        val purchase = purchaseRepository.findById(purchaseId)
            ?: throw IllegalArgumentException("Purchase not found: $purchaseId")

        if (purchase.buyerId != buyerId) {
            throw IllegalArgumentException("Purchase $purchaseId does not belong to buyer $buyerId")
        }

        if (purchase.status != PurchaseStatus.AWAITING_PAYMENT) {
            throw IllegalStateException("Purchase $purchaseId cannot be cancelled in status ${purchase.status}")
        }

        val updatedPurchase = purchase.copy(status = PurchaseStatus.CANCELLED)
        purchaseRepository.save(updatedPurchase)

        paymentService.cancelPaymentForPurchase(purchaseId)
        publishPurchaseEvent(updatedPurchase)

        log.info("Purchase $purchaseId cancelled by buyer $buyerId")
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

    fun getPurchasesForSeller(sellerId: ObjectId, pageable: Pageable): Page<Purchase> {
        return purchaseRepository.findAllBySellerId(sellerId, pageable)
    }

    fun getPurchasesForBuyer(buyerId: ObjectId, pageable: Pageable): Page<Purchase> {
        return purchaseRepository.findAllByBuyerId(buyerId, pageable)
    }
}
