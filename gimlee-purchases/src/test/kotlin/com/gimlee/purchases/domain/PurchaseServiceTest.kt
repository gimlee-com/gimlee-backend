package com.gimlee.purchases.domain

import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.events.PaymentEvent
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.persistence.PurchaseRepository
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import io.kotest.core.spec.style.StringSpec
import io.mockk.*
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import java.math.BigDecimal
import java.time.Instant

class PurchaseServiceTest : StringSpec({

    val purchaseRepository = mockk<PurchaseRepository>(relaxed = true)
    val paymentService = mockk<PaymentService>(relaxed = true)
    val adService = mockk<AdService>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = PurchaseService(purchaseRepository, paymentService, adService, eventPublisher)

    "should init purchase and payment and publish events" {
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val adId = ObjectId.get()
        val amount = BigDecimal("10.0")

        val purchaseSlot = slot<Purchase>()
        every { purchaseRepository.save(capture(purchaseSlot)) } answers { purchaseSlot.captured }

        service.initPurchase(buyerId, sellerId, adId, amount)

        verify { paymentService.initPayment(any(), buyerId, sellerId, amount, PaymentMethod.PIRATE_CHAIN) }
        verify(exactly = 2) { purchaseRepository.save(any()) } 
        verify(exactly = 2) { eventPublisher.publishEvent(any<PurchaseEvent>()) }
    }

    "should init purchase successfully if stock available" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Test Ad",
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAd(adId.toHexString()) } returns ad
        
        service.purchase(buyerId, adId, BigDecimal.TEN, Currency.ARRR)

        verify { purchaseRepository.save(match { it.status == PurchaseStatus.CREATED }) }
    }

    "should throw AdPriceMismatchException if price mismatch" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Test Ad",
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAd(adId.toHexString()) } returns ad
        
        io.kotest.assertions.throwables.shouldThrow<PurchaseService.AdPriceMismatchException> {
            service.purchase(buyerId, adId, BigDecimal.ONE, Currency.ARRR)
        }
    }

    "should update purchase status on payment complete event" {
        val purchaseId = ObjectId.get()
        val purchase = Purchase(
            id = purchaseId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            adId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            status = PurchaseStatus.AWAITING_PAYMENT,
            createdAt = Instant.now()
        )

        every { purchaseRepository.findById(purchaseId) } returns purchase

        val event = PaymentEvent(
            purchaseId = purchaseId,
            buyerId = purchase.buyerId,
            sellerId = purchase.sellerId,
            status = PaymentStatus.COMPLETE.id,
            paymentMethod = PaymentMethod.PIRATE_CHAIN.id,
            amount = purchase.amount,
            timestamp = Instant.now()
        )

        service.onPaymentEvent(event)

        verify { purchaseRepository.save(match { it.status == PurchaseStatus.COMPLETE }) }
    }
})
