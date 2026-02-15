package com.gimlee.api

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import java.math.BigDecimal

@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=true"])
class AdVisibilityIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository
) : BaseIntegrationTest({

    Given("ads with different statuses") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        val sellerIdStr = sellerId.toHexString()

        // 1. Create an ACTIVE ad
        val activeAd = adService.createAd(sellerIdStr, "Active Ad", null, 10)
        adService.updateAd(activeAd.id, sellerIdStr, UpdateAdRequest(
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(activeAd.id, sellerIdStr)

        // 2. Create an INACTIVE ad
        val inactiveAd = adService.createAd(sellerIdStr, "Inactive Ad", null, 10)

        When("fetching all ads via /ads/") {
            Then("only the active ad should be returned") {
                val response = restClient.get("/ads/")
                response.statusCode shouldBe 200
                val content = response.body ?: ""
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe false
            }
        }

        When("fetching featured ads via /ads/featured/") {
            Then("only the active ad should be returned") {
                val response = restClient.get("/ads/featured/")
                response.statusCode shouldBe 200
                val content = response.body ?: ""
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe false
            }
        }

        When("fetching my ads via /sales/ads/") {
            Then("both active and inactive ads should be returned") {
                val token = restClient.createAuthHeader(
                    subject = sellerIdStr,
                    username = "seller",
                    roles = listOf("USER")
                )
                val response = restClient.get("/sales/ads/", token)
                response.statusCode shouldBe 200
                val content = response.body ?: ""
                content.contains("Active Ad") shouldBe true
                content.contains("Inactive Ad") shouldBe true
            }
        }

        When("fetching a single inactive ad via /ads/{adId}") {
            Then("it should return 404 Not Found") {
                val response = restClient.get("/ads/${inactiveAd.id}")
                response.statusCode shouldBe 404
            }
        }

        When("fetching a single active ad via /ads/{adId}") {
            Then("it should return 200 OK") {
                val response = restClient.get("/ads/${activeAd.id}")
                response.statusCode shouldBe 200
            }
        }
    }
})
