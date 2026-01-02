package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdVisibilityIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository
) : BaseIntegrationTest({

    Given("ads with different statuses") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        val sellerIdStr = sellerId.toHexString()

        // 1. Create an ACTIVE ad
        val activeAd = adService.createAd(sellerIdStr, "Active Ad", 10)
        adService.updateAd(activeAd.id, sellerIdStr, UpdateAdRequest(
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(activeAd.id, sellerIdStr)

        // 2. Create an INACTIVE ad
        val inactiveAd = adService.createAd(sellerIdStr, "Inactive Ad", 10)
        // Remains INACTIVE by default

        // 3. Create another ad and DEACTIVATE it (though it's basically INACTIVE status)
        // Wait, how do I delete an ad? Let's check AdService
        // There is no delete method in AdService. Let's check AdStatus.
        // AdStatus has DELETED. Maybe it's set manually in DB or via some hidden method?
        // Let's check ManageAdController.

        When("fetching all ads via /ads/") {
            val result = mockMvc.get("/ads/") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString
            
            Then("only the active ad should be returned") {
                // We can use a simple check in content string or parse it
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe false
            }
        }

        When("fetching featured ads via /ads/featured/") {
            val result = mockMvc.get("/ads/featured/") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("only the active ad should be returned") {
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe false
            }
        }

        When("fetching my ads via /sales/ads/") {
            val principal = Principal(userId = sellerIdStr, username = "seller", roles = listOf(Role.USER))
            val result = mockMvc.get("/sales/ads/") {
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("both active and inactive ads should be returned") {
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe true
            }
        }

        When("fetching a single inactive ad via /ads/{adId}") {
            mockMvc.get("/ads/${inactiveAd.id}") {
            }.andExpect {
                // Currently it returns 200 because I haven't implemented filtering for single ad yet.
                // If I want to fix it, it should be 404.
                status { isNotFound() }
            }
        }

        When("fetching a single active ad via /ads/{adId}") {
            mockMvc.get("/ads/${activeAd.id}") {
            }.andExpect {
                status { isOk() }
            }
        }
    }
})
