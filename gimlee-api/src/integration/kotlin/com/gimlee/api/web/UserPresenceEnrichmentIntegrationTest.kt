package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.domain.model.UserPresenceStatus
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class UserPresenceEnrichmentIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val adService: AdService,
    private val profileService: ProfileService,
    private val userPresenceService: UserPresenceService
) : BaseIntegrationTest({

    Given("a user with presence, profile and ads") {
        val userId = ObjectId.get()
        val username = "enrichment_test_user"
        userRepository.save(User(id = userId, username = username))
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.PIRATE) // Required for ARRR settlement
        profileService.updateAvatar(userId.toHexString(), "http://avatar.url")
        
        // Track activity to set presence
        userPresenceService.trackActivity(userId.toHexString())
        userPresenceService.updateStatus(userId.toHexString(), UserPresenceStatus.AWAY, "Testing")
        userPresenceService.flushBuffer()

        val ad = adService.createAd(userId.toHexString(), "Enriched Ad", null, 1)
        adService.updateAd(ad.id, userId.toHexString(), UpdateAdRequest(
            description = "Test Description",
            price = CurrencyAmount(BigDecimal("100"), Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city", doubleArrayOf(0.0, 0.0)),
            stock = 1
        ))
        adService.activateAd(ad.id, userId.toHexString())

        When("fetching the user space via /spaces/user/{userName}") {
            val result = mockMvc.get("/spaces/user/$username") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("it should include presence information") {
                content.contains("\"username\":\"$username\"") shouldBe true
                content.contains("\"presence\":{") shouldBe true
                content.contains("\"status\":\"AWAY\"") shouldBe true
                content.contains("\"customStatus\":\"Testing\"") shouldBe true
            }
        }

        When("fetching the ad details via /ads/{adId}") {
            val result = mockMvc.get("/ads/${ad.id}") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("it should include user and presence information") {
                content.contains("\"title\":\"Enriched Ad\"") shouldBe true
                content.contains("\"user\":{") shouldBe true
                content.contains("\"username\":\"$username\"") shouldBe true
                content.contains("\"presence\":{") shouldBe true
                content.contains("\"status\":\"AWAY\"") shouldBe true
            }
        }
    }
})
