package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import com.gimlee.payments.persistence.ExchangeRateRepository
import com.gimlee.user.domain.UserPreferencesService
import com.mongodb.client.MongoDatabase
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdDiscoveryFilteringIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val adRepository: AdRepository,
    private val userRepository: UserRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val userPreferencesService: UserPreferencesService,
    private val userRoleRepository: UserRoleRepository,
    private val mongoDatabase: MongoDatabase
) : BaseIntegrationTest({

    beforeSpec {
        adRepository.clear()
        exchangeRateRepository.clear()
        mongoDatabase.getCollection(UserRepository.USERS_COLLECTION_NAME).deleteMany(org.bson.Document())
        mongoDatabase.getCollection(UserRoleRepository.USER_ROLES_COLLECTION_NAME).deleteMany(org.bson.Document())
    }

    Given("ads in different settlement currencies and exchange rates") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userRoleRepository.add(sellerId, Role.YCASH)
        val sellerIdStr = sellerId.toHexString()
        
        // Ad 1: 100 ARRR
        val ad1 = adService.createAd(sellerIdStr, "ARRR Ad", null, 10)
        adService.updateAd(ad1.id, sellerIdStr, UpdateAdRequest(
            description = "Description 1",
            price = CurrencyAmount(BigDecimal("100"), Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city1", doubleArrayOf(0.0, 0.0)),
            stock = 10
        ))
        adService.activateAd(ad1.id, sellerIdStr)

        // Ad 2: 100 YEC
        val ad2 = adService.createAd(sellerIdStr, "YEC Ad", null, 10)
        adService.updateAd(ad2.id, sellerIdStr, UpdateAdRequest(
            description = "Description 2",
            price = CurrencyAmount(BigDecimal("100"), Currency.YEC),
            location = com.gimlee.ads.domain.model.Location("city2", doubleArrayOf(0.0, 0.0)),
            stock = 10
        ))
        adService.activateAd(ad2.id, sellerIdStr)
        
        // Ad 3: 500 ARRR (outside range)
        val ad3 = adService.createAd(sellerIdStr, "Expensive Ad", null, 10)
        adService.updateAd(ad3.id, sellerIdStr, UpdateAdRequest(
            description = "Description 3",
            price = CurrencyAmount(BigDecimal("500"), Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city3", doubleArrayOf(0.0, 0.0)),
            stock = 10
        ))
        adService.activateAd(ad3.id, sellerIdStr)

        // Exchange rates:
        // 1 ARRR = 0.5 USD (so 100 ARRR = 50 USD)
        // 1 YEC = 0.5 USD (so 100 YEC = 50 USD)
        exchangeRateRepository.save(ExchangeRate(
            baseCurrency = Currency.ARRR,
            quoteCurrency = Currency.USD,
            rate = BigDecimal("0.5"),
            updatedAt = Instant.now(),
            source = "test"
        ))
        exchangeRateRepository.save(ExchangeRate(
            baseCurrency = Currency.YEC,
            quoteCurrency = Currency.USD,
            rate = BigDecimal("0.5"),
            updatedAt = Instant.now(),
            source = "test"
        ))

        When("filtering by price range 40-60 USD (as a guest, default currency USD)") {
            val result = mockMvc.get("/ads/") {
                param("minp", "40")
                param("maxp", "60")
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString
            
            Then("it should find both the 100 ARRR ad and the 100 YEC ad") {
                content.contains("ARRR Ad") shouldBe true
                content.contains("YEC Ad") shouldBe true
                content.contains("Expensive Ad") shouldBe false
            }
        }
        
        When("filtering by price range 90-110 ARRR (user prefers ARRR)") {
            val userId = ObjectId.get().toHexString()
            userPreferencesService.updateUserPreferences(userId, "en-US", "ARRR")
            val principal = Principal(userId = userId, username = "arrruser", roles = listOf(Role.USER))

            // 100 ARRR is in range 90-110 ARRR
            // 100 YEC = 50 USD = 100 ARRR (via USD)
            
            // Add ARRR to YEC rate for direct conversion or through USD
            exchangeRateRepository.save(ExchangeRate(
                baseCurrency = Currency.YEC,
                quoteCurrency = Currency.ARRR,
                rate = BigDecimal("1.0"),
                updatedAt = Instant.now(),
                source = "test"
            ))

            val result = mockMvc.get("/ads/") {
                requestAttr("principal", principal)
                param("minp", "90")
                param("maxp", "110")
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("it should find both the 100 ARRR ad and the 100 YEC ad") {
                content.contains("ARRR Ad") shouldBe true
                content.contains("YEC Ad") shouldBe true
                content.contains("Expensive Ad") shouldBe false
            }
        }
    }
})
