package com.gimlee.api

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.toMicros
import com.gimlee.events.PaymentEvent
import com.gimlee.orders.domain.OrderService
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant

class OrderFlowIntegrationTest(
    private val adService: AdService,
    private val orderService: OrderService,
    private val userRoleRepository: UserRoleRepository,
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository,
    private val eventPublisher: ApplicationEventPublisher
) : BaseIntegrationTest({

    Given("a seller with PIRATE role and an active Ad") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)

        // Add Pirate Chain address for the seller
        val addressInfo = PirateChainAddressInfo(
            zAddress = "zs1testaddress",
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userPirateChainAddressRepository.addAddressToUser(sellerId, addressInfo)

        val ad = adService.createAd(sellerId.toHexString(), "Test Item", 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            description = "Test Description",
            price = CurrencyAmount(BigDecimal("10.00"), Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        And("a buyer") {
            val buyerId = ObjectId.get()

            When("the buyer places an order") {
                val order = orderService.placeOrder(buyerId, ObjectId(ad.id), BigDecimal("10.00"), Currency.ARRR)

                Then("the order status should be AWAITING_PAYMENT") {
                    val savedOrder = orderService.getOrder(order.id)
                    savedOrder?.status shouldBe OrderStatus.AWAITING_PAYMENT
                }

                Then("the Ad stock should have 1 item locked") {
                    val updatedAd = adService.getAd(ad.id)
                    updatedAd?.lockedStock shouldBe 1
                    updatedAd?.stock shouldBe 10
                    updatedAd?.availableStock shouldBe 9
                }

                And("a payment complete event is received") {
                    val paymentEvent = PaymentEvent(
                        orderId = order.id,
                        buyerId = buyerId,
                        sellerId = sellerId,
                        status = PaymentStatus.COMPLETE.id,
                        paymentMethod = PaymentMethod.PIRATE_CHAIN.id,
                        amount = BigDecimal("10.00"),
                        timestamp = Instant.now()
                    )
                    eventPublisher.publishEvent(paymentEvent)

                    Then("the order status should be COMPLETE") {
                        val finalOrder = orderService.getOrder(order.id)
                        finalOrder?.status shouldBe OrderStatus.COMPLETE
                    }

                    Then("the Ad stock should be reduced and locked stock cleared") {
                        val finalAd = adService.getAd(ad.id)
                        finalAd?.stock shouldBe 9
                        finalAd?.lockedStock shouldBe 0
                        finalAd?.availableStock shouldBe 9
                    }
                }
            }
        }
    }
})
