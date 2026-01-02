package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.api.web.dto.PurchaseResponseDto
import com.gimlee.api.web.dto.PurchaseStatusResponseDto
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.toMicros
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import com.gimlee.purchases.web.dto.request.PurchaseRequestDto
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class PurchaseFacadeIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository
) : BaseIntegrationTest({

    Given("a seller and an active Ad") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)

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
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer", roles = listOf(Role.USER))

            When("the buyer makes a purchase through the facade") {
                val request = PurchaseRequestDto(
                    items = listOf(PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("10.00"))),
                    currency = Currency.ARRR
                )
                
                val result = mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isCreated() }
                }.andReturn()

                val response = objectMapper.readValue(result.response.contentAsString, PurchaseResponseDto::class.java)

                Then("the response should contain payment details") {
                    response.purchaseId shouldNotBe null
                    response.status shouldBe PurchaseStatus.AWAITING_PAYMENT.name
                    response.payment shouldNotBe null
                    response.payment?.address shouldBe "zs1testaddress"
                    response.payment?.amount shouldBe BigDecimal("10.00")
                    response.payment?.memo shouldNotBe null
                    response.payment?.qrCodeUri shouldBe "pirate:zs1testaddress?amount=10.00"
                }

                And("the buyer checks the purchase status") {
                    val statusResult = mockMvc.get("/purchases/${response.purchaseId}/status") {
                        requestAttr("principal", principal)
                    }.andExpect {
                        status { isOk() }
                    }.andReturn()

                    val statusResponse = objectMapper.readValue(statusResult.response.contentAsString, PurchaseStatusResponseDto::class.java)

                    Then("the status should be AWAITING_PAYMENT") {
                        statusResponse.purchaseId shouldBe response.purchaseId
                        statusResponse.status shouldBe PurchaseStatus.AWAITING_PAYMENT.name
                        statusResponse.paymentStatus shouldBe "AWAITING_CONFIRMATION"
                    }
                }
            }

            When("the buyer attempts to make a purchase with a mismatched amount") {
                val maliciousRequest = PurchaseRequestDto(
                    items = listOf(PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("1.00"))),
                    currency = Currency.ARRR
                )

                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(maliciousRequest)
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error") { value("PRICE_MISMATCH") }
                    jsonPath("$.currentPrice.amount") { value(10.0) }
                }
            }

            When("the buyer attempts to make a purchase with items from multiple sellers") {
                val anotherSellerId = ObjectId.get()
                userRoleRepository.add(anotherSellerId, Role.USER)
                userRoleRepository.add(anotherSellerId, Role.PIRATE)

                val ad2 = adService.createAd(anotherSellerId.toHexString(), "Another Seller Item", 5)
                adService.updateAd(ad2.id, anotherSellerId.toHexString(), UpdateAdRequest(
                    description = "Another Description",
                    price = CurrencyAmount(BigDecimal("5.00"), Currency.ARRR),
                    location = Location("city2", doubleArrayOf(3.0, 4.0)),
                    stock = 5
                ))
                adService.activateAd(ad2.id, anotherSellerId.toHexString())

                val request = PurchaseRequestDto(
                    items = listOf(
                        PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("10.00")),
                        PurchaseItemRequestDto(adId = ad2.id, quantity = 1, unitPrice = BigDecimal("5.00"))
                    ),
                    currency = Currency.ARRR
                )

                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error") { value("All items in a purchase must belong to the same seller.") }
                }
            }

            When("the buyer attempts to purchase multiple items with individual price mismatches but same total") {
                val ad2 = adService.createAd(sellerId.toHexString(), "Same Seller Item", 5)
                adService.updateAd(ad2.id, sellerId.toHexString(), UpdateAdRequest(
                    description = "Same Seller Item Description",
                    price = CurrencyAmount(BigDecimal("20.00"), Currency.ARRR),
                    location = Location("city1", doubleArrayOf(1.0, 2.0)),
                    stock = 5
                ))
                adService.activateAd(ad2.id, sellerId.toHexString())

                // Actual: ad1=10.00, ad2=20.00 (Total 30.00)
                // Request: ad1=15.00, ad2=15.00 (Total 30.00)
                val request = PurchaseRequestDto(
                    items = listOf(
                        PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("15.00")),
                        PurchaseItemRequestDto(adId = ad2.id, quantity = 1, unitPrice = BigDecimal("15.00"))
                    ),
                    currency = Currency.ARRR
                )

                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error") { value("PRICE_MISMATCH") }
                }
            }
        }
    }
})
