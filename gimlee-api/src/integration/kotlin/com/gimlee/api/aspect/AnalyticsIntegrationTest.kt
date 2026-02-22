package com.gimlee.api.aspect

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.persistence.AdVisitRepository
import com.gimlee.analytics.persistence.AnalyticsEventRepository
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.concurrent.TimeUnit

@AutoConfigureMockMvc
@TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AnalyticsIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val analyticsEventRepository: AnalyticsEventRepository,
    private val adVisitRepository: AdVisitRepository
) : BaseIntegrationTest({

    beforeSpec {
        analyticsEventRepository.clear()
        adVisitRepository.clear()
    }

    Given("an ad and a user") {
        val sellerId = ObjectId.get()
        userRepository.save(User(id = sellerId, username = "seller"))
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        val ad = adService.createAd(sellerId.toHexString(), "Test Ad", null, 10)
        adService.updateAd(ad.id, sellerId.toHexString(), com.gimlee.ads.domain.model.UpdateAdRequest(
            description = "Description",
            price = com.gimlee.ads.domain.model.CurrencyAmount(java.math.BigDecimal("100"), com.gimlee.common.domain.model.Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = Location("city", doubleArrayOf(1.0, 1.0))
        ))
        adService.activateAd(ad.id, sellerId.toHexString())
        
        When("viewing the ad via GET /ads/{adId}") {
            mockMvc.get("/ads/${ad.id}") {
                header("X-Client-Id", "test-client")
            }.andExpect {
                status { isOk() }
            }

            Then("it should record an analytics event and increment ad visit count") {
                await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                    // Check ad visit repository (gimlee-ads side)
                    adVisitRepository.getVisitCount(ad.id, 20200101, 20991231) shouldBe 1
                    
                    // Check analytics event repository (gimlee-analytics side)
                    analyticsEventRepository.count() shouldBe 1
                }
            }
        }
        
        When("a bot visits the ad") {
            mockMvc.get("/ads/${ad.id}") {
                header("X-Client-Id", "bot-client")
                header("X-Crowdsec-Bot-Score", "0.9")
            }.andExpect {
                status { isOk() }
            }
            
            Then("it should NOT increment the visit count") {
                // Wait a bit to be sure it was processed (or rather NOT processed)
                Thread.sleep(500)
                adVisitRepository.getVisitCount(ad.id, 20200101, 20991231) shouldBe 1
            }
        }
    }
})
