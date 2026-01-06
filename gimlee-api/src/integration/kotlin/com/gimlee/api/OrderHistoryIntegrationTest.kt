package com.gimlee.api
import com.gimlee.common.domain.model.Currency

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class OrderHistoryIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val purchaseService: PurchaseService,
    private val userRoleRepository: UserRoleRepository,
    private val userRepository: UserRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository
) : BaseIntegrationTest({

    Given("a seller and a buyer") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userRepository.save(User(id = sellerId, username = "seller_user"))

        val addressInfo = WalletAddressInfo(
            type = Currency.ARRR,
            zAddress = "zs1testaddress",
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userWalletAddressRepository.addAddressToUser(sellerId, addressInfo)

        val buyerId = ObjectId.get()
        userRepository.save(User(id = buyerId, username = "buyer_user"))

        val ad = adService.createAd(sellerId.toHexString(), "Test Ad Title", 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            description = "Desc",
            price = CurrencyAmount(BigDecimal("50.00"), Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        And("a purchase has been made") {
            val purchase = purchaseService.purchase(
                buyerId,
                listOf(PurchaseItemRequestDto(ad.id, 2, BigDecimal("50.00"))),
                Currency.ARRR
            )

            When("the seller fetches their sales orders") {
                val principal = Principal(userId = sellerId.toHexString(), username = "seller_user", roles = listOf(Role.USER, Role.PIRATE))
                
                val result = mockMvc.get("/sales/orders/") {
                    requestAttr("principal", principal)
                }.andExpect {
                    status { isOk() }
                }.andReturn()

                val content = result.response.contentAsString
                
                Then("the response should contain enriched order data") {
                    content.contains("Test Ad Title") shouldBe true
                    content.contains("buyer_user") shouldBe true
                    content.contains("AWAITING_PAYMENT") shouldBe true
                    content.contains("50.00") shouldBe true
                    content.contains("100.00") shouldBe true
                    content.contains("ARRR") shouldBe true
                }
            }

            When("the seller fetches a single sales order") {
                val principal = Principal(userId = sellerId.toHexString(), username = "seller_user", roles = listOf(Role.USER, Role.PIRATE))

                val result = mockMvc.get("/sales/orders/${purchase.id}") {
                    requestAttr("principal", principal)
                }.andExpect {
                    status { isOk() }
                }.andReturn()

                val content = result.response.contentAsString

                Then("the response should contain full enriched order details") {
                    content.contains(purchase.id.toHexString()) shouldBe true
                    content.contains("Test Ad Title") shouldBe true
                    content.contains("buyer_user") shouldBe true
                }
            }

            When("another user tries to fetch the seller's order") {
                val otherPrincipal = Principal(userId = ObjectId.get().toHexString(), username = "other", roles = listOf(Role.USER))

                mockMvc.get("/sales/orders/${purchase.id}") {
                    requestAttr("principal", otherPrincipal)
                }.andExpect {
                    status { isNotFound() }
                }
            }

            When("the buyer fetches their purchase history") {
                val buyerPrincipal = Principal(userId = buyerId.toHexString(), username = "buyer_user", roles = listOf(Role.USER))

                val result = mockMvc.get("/purchases/") {
                    requestAttr("principal", buyerPrincipal)
                }.andExpect {
                    status { isOk() }
                }.andReturn()

                val content = result.response.contentAsString

                Then("the response should contain the buyer's purchase with enriched seller data") {
                    content.contains("Test Ad Title") shouldBe true
                    content.contains("seller_user") shouldBe true
                    content.contains("50.00") shouldBe true
                }
            }
        }
    }
})
