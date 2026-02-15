package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import com.gimlee.user.domain.UserPreferencesService
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdDiscoveryIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val adRepository: com.gimlee.ads.persistence.AdRepository,
    private val userRepository: UserRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userPreferencesService: UserPreferencesService,
    private val userRoleRepository: UserRoleRepository,
    private val mongoDatabase: com.mongodb.client.MongoDatabase
) : BaseIntegrationTest({

    beforeSpec {
        adRepository.clear()
        exchangeRateRepository.clear()
        mongoDatabase.getCollection(UserRepository.USERS_COLLECTION_NAME).deleteMany(org.bson.Document())
        mongoDatabase.getCollection(UserRoleRepository.USER_ROLES_COLLECTION_NAME).deleteMany(org.bson.Document())
        mongoDatabase.getCollection("gimlee-user-preferences").deleteMany(org.bson.Document())
    }

    Given("an ad and exchange rates") {
        val sellerId = ObjectId.get()
        userRepository.save(User(id = sellerId, username = "seller"))
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        val sellerIdStr = sellerId.toHexString()
        
        val ad = adService.createAd(sellerIdStr, "Test Ad", null, 10)
        adService.updateAd(ad.id, sellerIdStr, UpdateAdRequest(
            description = "Description",
            price = CurrencyAmount(BigDecimal("100"), Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city", doubleArrayOf(0.0, 0.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerIdStr)

        // ARRR to USD rate: 1 ARRR = 0.5 USD
        exchangeRateRepository.save(ExchangeRate(
            baseCurrency = Currency.ARRR,
            quoteCurrency = Currency.USD,
            rate = BigDecimal("0.5"),
            updatedAt = Instant.now(),
            source = "test"
        ))

        When("fetching the ad as a guest (default currency USD)") {
            val result = mockMvc.get("/ads/${ad.id}") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString
            
            Then("it should include the price in USD") {
                content.contains("\"price\":{\"amount\":100,\"currency\":\"ARRR\"}") shouldBe true
                // USD has 2 decimal places. 100 * 0.5 = 50.00
                content.contains("\"preferredPrice\":{\"amount\":50.00,\"currency\":\"USD\"}") shouldBe true
                content.contains("\"memberSince\":") shouldBe true
            }
        }

        When("fetching the ad as a user with preferred currency ARRR") {
            val userId = ObjectId.get().toHexString()
            userPreferencesService.updateUserPreferences(userId, "en-US", "ARRR")
            val principal = Principal(userId = userId, username = "testuser", roles = listOf(Role.USER))

            val result = mockMvc.get("/ads/${ad.id}") {
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("it should include the price in ARRR") {
                // ARRR has 8 decimal places.
                content.contains("\"preferredPrice\":{\"amount\":100.00000000,\"currency\":\"ARRR\"}") shouldBe true
            }
        }
        
        When("fetching multiple ads") {
             val result = mockMvc.get("/ads/") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString
            
            Then("they should also include preferredPrice") {
                content.contains("\"preferredPrice\":{\"amount\":50.00,\"currency\":\"USD\"}") shouldBe true
            }
        }
    }
})
