package com.gimlee.api

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.api.web.dto.AdDiscoveryDetailsDto

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = [
    "gimlee.auth.jwt.enabled=false",
    "gimlee.ads.discovery.other-ads-count=3"
])
class OtherAdsIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val objectMapper: ObjectMapper
) : BaseIntegrationTest({

    Given("a user with multiple ads") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        val sellerIdStr = sellerId.toHexString()

        // Create 7 active ads
        val ads = (1..7).map { i ->
            val ad = adService.createAd(sellerIdStr, "Ad $i", null, 10)
            adService.updateAd(ad.id, sellerIdStr, UpdateAdRequest(
                description = "Desc $i",
                price = CurrencyAmount(BigDecimal.valueOf(i.toLong()), Currency.ARRR),
                location = com.gimlee.ads.domain.model.Location("city1", doubleArrayOf(1.0, 2.0)),
                stock = 10
            ))
            adService.activateAd(ad.id, sellerIdStr)
        }

        When("fetching details for one of the ads") {
            val targetAd = ads[0]
            val result = mockMvc.get("/ads/${targetAd.id}") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val response = objectMapper.readValue(result.response.contentAsString, AdDiscoveryDetailsDto::class.java)

            Then("it should return configured number of most recent other ads") {
                response.otherAds?.shouldHaveSize(3)
                response.otherAds?.any { it.id == targetAd.id } shouldBe false
                
                val otherAdTitles = response.otherAds?.map { it.title } ?: emptyList()
                otherAdTitles.contains("Ad 7") shouldBe true
                otherAdTitles.contains("Ad 6") shouldBe true
                otherAdTitles.contains("Ad 5") shouldBe true
                otherAdTitles.contains("Ad 4") shouldBe false
            }
        }
    }
})
