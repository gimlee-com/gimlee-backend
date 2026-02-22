package com.gimlee.purchases.domain

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.common.domain.model.Currency
import com.gimlee.events.PaymentEvent
import com.gimlee.events.PurchaseEvent
import com.gimlee.payments.domain.PaymentService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.domain.service.VolatilityStateService
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.PurchaseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant

class PurchaseServiceTest : StringSpec({

    val purchaseRepository = mockk<PurchaseRepository>(relaxed = true)
    val paymentService = mockk<PaymentService>(relaxed = true)
    val adService = mockk<AdService>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val volatilityStateService = mockk<VolatilityStateService>(relaxed = true)
    val service = PurchaseService(purchaseRepository, paymentService, adService, eventPublisher, volatilityStateService)

    "should init purchase and payment and publish events" {
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val adId = ObjectId.get()
        val amount = BigDecimal("10.0")
        val items = listOf(com.gimlee.purchases.domain.model.PurchaseItem(adId, 1, amount, Currency.ARRR))

        val purchaseSlot = slot<Purchase>()
        every { purchaseRepository.save(capture(purchaseSlot)) } answers { purchaseSlot.captured }

        service.initPurchase(buyerId, sellerId, items, amount, PaymentMethod.PIRATE_CHAIN)

        verify { paymentService.initPayment(any(), buyerId, sellerId, amount, PaymentMethod.PIRATE_CHAIN) }
        verify(exactly = 2) { purchaseRepository.save(any()) } 
        verify(exactly = 2) { eventPublisher.publishEvent(any<PurchaseEvent>()) }
    }

    "should reject purchase if ad is volatile and protection enabled" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Volatile Ad",
            description = "Desc",
            pricingMode = com.gimlee.ads.domain.model.PricingMode.PEGGED,
            price = CurrencyAmount(BigDecimal.TEN, Currency.USD),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0,
            volatilityProtection = true
        )

        every { adService.getAds(listOf(adId.toHexString())) } returns listOf(ad)
        every { volatilityStateService.isFrozen(Currency.ARRR) } returns true

        val exception = io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
            service.purchase(
                buyerId,
                listOf(com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId.toHexString(), 1, BigDecimal.TEN)),
                Currency.ARRR
            )
        }
        exception.message shouldContain "Purchase temporarily suspended"
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
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAds(listOf(adId.toHexString())) } returns listOf(ad)
        
        service.purchase(buyerId, listOf(com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId.toHexString(), 1, BigDecimal.TEN)), Currency.ARRR)

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
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAds(listOf(adId.toHexString())) } returns listOf(ad)
        
        val exception = io.kotest.assertions.throwables.shouldThrow<PurchaseService.AdPriceMismatchException> {
            service.purchase(buyerId, listOf(com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId.toHexString(), 1, BigDecimal.ONE)), Currency.ARRR)
        }
        exception.currentPrices[adId.toHexString()]?.amount?.compareTo(BigDecimal.TEN) shouldBe 0
    }

    "should throw IllegalArgumentException if items from multiple sellers" {
        val buyerId = ObjectId.get()
        val adId1 = ObjectId.get()
        val adId2 = ObjectId.get()
        val sellerId1 = ObjectId.get()
        val sellerId2 = ObjectId.get()

        val ad1 = Ad(
            id = adId1.toHexString(),
            userId = sellerId1.toHexString(),
            title = "Ad 1",
            description = "Desc 1",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )
        val ad2 = Ad(
            id = adId2.toHexString(),
            userId = sellerId2.toHexString(),
            title = "Ad 2",
            description = "Desc 2",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAds(match { it.containsAll(listOf(adId1.toHexString(), adId2.toHexString())) }) } returns listOf(ad1, ad2)

        val items = listOf(
            com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId1.toHexString(), 1, BigDecimal.TEN),
            com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId2.toHexString(), 1, BigDecimal.TEN)
        )

        val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            service.purchase(buyerId, items, Currency.ARRR)
        }
        exception.message shouldBe "All items in a purchase must belong to the same seller."
    }

    "should throw AdPriceMismatchException if one of multiple items has price mismatch" {
        val buyerId = ObjectId.get()
        val adId1 = ObjectId.get()
        val adId2 = ObjectId.get()
        val sellerId = ObjectId.get()

        val ad1 = Ad(
            id = adId1.toHexString(),
            userId = sellerId.toHexString(),
            title = "Ad 1",
            description = "Desc 1",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )
        val ad2 = Ad(
            id = adId2.toHexString(),
            userId = sellerId.toHexString(),
            title = "Ad 2",
            description = "Desc 2",
            price = CurrencyAmount(BigDecimal("20.0"), Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAds(match { it.containsAll(listOf(adId1.toHexString(), adId2.toHexString())) }) } returns listOf(ad1, ad2)

        // Total would be 30.0. Requesting 15+15=30.0 should still fail because individually they don't match.
        val items = listOf(
            com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId1.toHexString(), 1, BigDecimal("15.0")),
            com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId2.toHexString(), 1, BigDecimal("15.0"))
        )

        val exception = io.kotest.assertions.throwables.shouldThrow<PurchaseService.AdPriceMismatchException> {
            service.purchase(buyerId, items, Currency.ARRR)
        }
        exception.currentPrices.size shouldBe 2
        exception.currentPrices[adId1.toHexString()]?.amount?.compareTo(BigDecimal.TEN) shouldBe 0
        exception.currentPrices[adId2.toHexString()]?.amount?.compareTo(BigDecimal("20.0")) shouldBe 0
    }

    "should throw IllegalStateException if ad is not ACTIVE" {
        val buyerId = ObjectId.get()
        val adId = ObjectId.get()
        val sellerId = ObjectId.get()
        val ad = Ad(
            id = adId.toHexString(),
            userId = sellerId.toHexString(),
            title = "Inactive Ad",
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            status = AdStatus.INACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            location = null,
            mainPhotoPath = null,
            stock = 5,
            lockedStock = 0
        )

        every { adService.getAds(listOf(adId.toHexString())) } returns listOf(ad)

        val exception = io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
            service.purchase(buyerId, listOf(com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto(adId.toHexString(), 1, BigDecimal.TEN)), Currency.ARRR)
        }
        exception.message shouldBe "One or more ads are not active: ${adId.toHexString()}"
    }

    "should update purchase status on payment complete event" {
        val purchaseId = ObjectId.get()
        val purchase = Purchase(
            id = purchaseId,
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            items = listOf(com.gimlee.purchases.domain.model.PurchaseItem(ObjectId.get(), 1, BigDecimal("10.0"), Currency.ARRR)),
            totalAmount = BigDecimal("10.0"),
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
            amount = purchase.totalAmount,
            timestamp = Instant.now()
        )

        service.onPaymentEvent(event)

        verify { purchaseRepository.save(match { it.status == PurchaseStatus.COMPLETE }) }
    }
})
