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
                val request = PurchaseRequestDto(adId = ad.id, amount = BigDecimal("10.00"), currency = Currency.ARRR)
                
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
                val maliciousRequest = PurchaseRequestDto(adId = ad.id, amount = BigDecimal("1.00"), currency = Currency.ARRR)

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
        }
    }
})
