package com.gimlee.api

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.events.PaymentEvent
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant

class PurchaseFlowIntegrationTest(
    private val adService: AdService,
    private val purchaseService: PurchaseService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val eventPublisher: ApplicationEventPublisher
) : BaseIntegrationTest({

    Given("a seller with PIRATE role and an active Ad") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)

        // Add Pirate Chain address for the seller
        val addressInfo = WalletAddressInfo(
            type = Currency.ARRR,
            zAddress = "zs1testaddress",
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userWalletAddressRepository.addAddressToUser(sellerId, addressInfo)

        val ad = adService.createAd(sellerId.toHexString(), "Test Item", null, 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            description = "Test Description",
            price = CurrencyAmount(BigDecimal("10.00"), Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        And("a buyer") {
            val buyerId = ObjectId.get()

            When("the buyer makes a purchase") {
                val purchase = purchaseService.purchase(
                    buyerId,
                    listOf(PurchaseItemRequestDto(ad.id, 1, BigDecimal("10.00"))),
                    Currency.ARRR
                )

                Then("the purchase status should be AWAITING_PAYMENT") {
                    val savedPurchase = purchaseService.getPurchase(purchase.id)
                    savedPurchase?.status shouldBe PurchaseStatus.AWAITING_PAYMENT
                }

                Then("the Ad stock should have 1 item locked") {
                    val updatedAd = adService.getAd(ad.id)
                    updatedAd?.lockedStock shouldBe 1
                    updatedAd?.stock shouldBe 10
                    updatedAd?.availableStock shouldBe 9
                }

                And("a payment complete event is received") {
                    val paymentEvent = PaymentEvent(
                        purchaseId = purchase.id,
                        buyerId = buyerId,
                        sellerId = sellerId,
                        status = PaymentStatus.COMPLETE.id,
                        paymentMethod = PaymentMethod.PIRATE_CHAIN.id,
                        amount = BigDecimal("10.00"),
                        timestamp = Instant.now()
                    )
                    eventPublisher.publishEvent(paymentEvent)

                    Then("the purchase status should be COMPLETE") {
                        val finalPurchase = purchaseService.getPurchase(purchase.id)
                        finalPurchase?.status shouldBe PurchaseStatus.COMPLETE
                    }

                    Then("the Ad stock should be reduced and locked stock cleared") {
                        val finalAd = adService.getAd(ad.id)
                        finalAd?.stock shouldBe 9
                        finalAd?.lockedStock shouldBe 0
                        finalAd?.availableStock shouldBe 9
                        finalAd?.status shouldBe com.gimlee.ads.domain.model.AdStatus.ACTIVE
                    }
                }
            }

            When("the buyer makes a purchase for all remaining items") {
                val currentAd = adService.getAd(ad.id)!!
                val remainingStock = currentAd.stock
                val purchase = purchaseService.purchase(
                    buyerId,
                    listOf(PurchaseItemRequestDto(ad.id, remainingStock, BigDecimal("10.00"))),
                    Currency.ARRR
                )

                And("the payment is complete") {
                    val paymentEvent = PaymentEvent(
                        purchaseId = purchase.id,
                        buyerId = buyerId,
                        sellerId = sellerId,
                        status = PaymentStatus.COMPLETE.id,
                        paymentMethod = PaymentMethod.PIRATE_CHAIN.id,
                        amount = BigDecimal("10.00").multiply(BigDecimal(remainingStock)),
                        timestamp = Instant.now()
                    )
                    eventPublisher.publishEvent(paymentEvent)

                    Then("the Ad should become INACTIVE because stock is 0") {
                        val finalAd = adService.getAd(ad.id)
                        finalAd?.stock shouldBe 0
                        finalAd?.status shouldBe com.gimlee.ads.domain.model.AdStatus.INACTIVE
                    }
                }
            }
        }
    }
})
