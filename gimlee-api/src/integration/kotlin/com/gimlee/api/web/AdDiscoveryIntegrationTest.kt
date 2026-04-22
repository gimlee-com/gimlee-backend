package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
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
import java.math.BigDecimal
import java.time.Instant

@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=true"])
class AdDiscoveryIntegrationTest(
    private val adService: AdService,
    private val adRepository: com.gimlee.ads.persistence.AdRepository,
    private val userRepository: UserRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userPreferencesService: UserPreferencesService,
    private val userRoleRepository: UserRoleRepository,
    private val mongoDatabase: com.mongodb.client.MongoDatabase
) : BaseIntegrationTest({

    adRepository.clear()
    exchangeRateRepository.clear()
    mongoDatabase.getCollection(UserRepository.USERS_COLLECTION_NAME).deleteMany(org.bson.Document())
    mongoDatabase.getCollection(UserRoleRepository.USER_ROLES_COLLECTION_NAME).deleteMany(org.bson.Document())
    mongoDatabase.getCollection("gimlee-user-preferences").deleteMany(org.bson.Document())

    Given("an ad and exchange rates") {
        val sellerId = ObjectId.get()
        userRepository.save(User(id = sellerId, username = "seller"))
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        val sellerIdStr = sellerId.toHexString()
        
        val ad = adService.createAd(sellerIdStr, "Test Ad", null, 10)
        adService.updateAd(ad.id, sellerIdStr, UpdateAdRequest(
            description = "Description",
            fixedPrices = mapOf(Currency.ARRR to BigDecimal("100")),
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
            Then("it should include the price in USD") {
                val response = restClient.get("/ads/${ad.id}")
                response.statusCode shouldBe 200

                val content = response.body ?: ""

                content.contains("\"settlementPrices\":[{\"amount\":100") shouldBe true
                // USD has 2 decimal places. 100 * 0.5 = 50.00
                content.contains("\"preferredPrice\":{\"amount\":50.00,\"currency\":\"USD\"}") shouldBe true
                content.contains("\"preferredPrices\":{\"currency\":\"USD\",\"prices\":{\"ARRR\":50.00}}") shouldBe true
                content.contains("\"availableStock\":10") shouldBe true
                content.contains("\"memberSince\":") shouldBe true
            }
        }

        When("fetching the ad as a user with preferred currency ARRR") {
            Then("it should include the price in ARRR") {
                val userId = ObjectId.get().toHexString()
                userPreferencesService.updateUserPreferences(userId, "en-US", "ARRR", null)
                val token = restClient.createAuthHeader(
                    subject = userId,
                    username = "testuser",
                    roles = listOf("USER")
                )

                val response = restClient.get("/ads/${ad.id}", token)
                response.statusCode shouldBe 200

                val content = response.body ?: ""
                
                // ARRR has 8 decimal places.
                content.contains("\"preferredPrice\":{\"amount\":100.00000000,\"currency\":\"ARRR\"}") shouldBe true
                content.contains("\"preferredPrices\":{\"currency\":\"ARRR\",\"prices\":{\"ARRR\":100.00000000}}") shouldBe true
            }
        }
        
        When("fetching multiple ads") {
            Then("they should also include preferredPrice and preferredPrices") {
                val response = restClient.get("/ads/")
                response.statusCode shouldBe 200

                val content = response.body ?: ""
                content.contains("\"preferredPrice\":{\"amount\":50.00,\"currency\":\"USD\"}") shouldBe true
                content.contains("\"preferredPrices\":{\"currency\":\"USD\",\"prices\":{\"ARRR\":50.00}}") shouldBe true
            }
        }
    }

    Given("a multi-currency FIXED_CRYPTO ad") {
        val sellerId = ObjectId.get()
        userRepository.save(User(id = sellerId, username = "multi-seller"))
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userRoleRepository.add(sellerId, Role.YCASH)
        val sellerIdStr = sellerId.toHexString()

        val ad = adService.createAd(sellerIdStr, "Multi-Currency Ad", null, 10)
        adService.updateAd(ad.id, sellerIdStr, UpdateAdRequest(
            description = "A multi-currency ad",
            fixedPrices = mapOf(Currency.ARRR to BigDecimal("100"), Currency.YEC to BigDecimal("5000")),
            location = com.gimlee.ads.domain.model.Location("city", doubleArrayOf(0.0, 0.0)),
            stock = 5
        ))
        adService.activateAd(ad.id, sellerIdStr)

        // ARRR to USD: 1 ARRR = 0.5 USD
        exchangeRateRepository.save(ExchangeRate(
            baseCurrency = Currency.ARRR,
            quoteCurrency = Currency.USD,
            rate = BigDecimal("0.5"),
            updatedAt = Instant.now(),
            source = "test"
        ))
        // YEC to USD: 1 YEC = 0.02 USD
        exchangeRateRepository.save(ExchangeRate(
            baseCurrency = Currency.YEC,
            quoteCurrency = Currency.USD,
            rate = BigDecimal("0.02"),
            updatedAt = Instant.now(),
            source = "test"
        ))

        When("fetching the ad as a guest") {
            Then("preferredPrices should include both currencies converted to USD") {
                val response = restClient.get("/ads/${ad.id}")
                response.statusCode shouldBe 200

                val content = response.body ?: ""

                // 100 ARRR * 0.5 = 50.00 USD, 5000 YEC * 0.02 = 100.00 USD
                content.contains("\"preferredPrices\"") shouldBe true
                content.contains("\"currency\":\"USD\"") shouldBe true
                content.contains("\"ARRR\":50.00") shouldBe true
                content.contains("\"YEC\":100.00") shouldBe true
            }
        }
    }
})
