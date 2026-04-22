package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import java.math.BigDecimal

class AdSettlementCurrencyIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository
) : BaseIntegrationTest({

    fun setupUser(vararg roles: Role): ObjectId {
        val userId = ObjectId.get()
        roles.forEach { userRoleRepository.add(userId, it) }
        return userId
    }

    fun authHeaders(userId: ObjectId, vararg roles: String): Map<String, String> {
        return restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = roles.toList()
        )
    }

    Given("a FIXED_CRYPTO ad and settlement currency validation") {
        val userId = setupUser(Role.USER, Role.PIRATE)
        val headers = authHeaders(userId, "USER", "PIRATE")

        When("setting a non-settlement currency (USD) in fixedPrices") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("USD" to 100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_INVALID_CURRENCY") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_INVALID_CURRENCY"
            }
        }

        When("setting a settlement currency (ARRR) with PIRATE role") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("ARRR" to 100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val updatedAd = adService.getAd(ad.id)
                updatedAd?.fixedPrices?.keys shouldBe setOf(Currency.ARRR)
            }
        }

        When("setting a settlement currency (ARRR) without PIRATE role") {
            val userNoPirate = setupUser(Role.USER)
            val headersNoPirate = authHeaders(userNoPirate, "USER")
            val ad = adService.createAd(userNoPirate.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("ARRR" to 100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headersNoPirate)

            Then("it should return 403 with AD_PIRATE_ROLE_REQUIRED") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_PIRATE_ROLE_REQUIRED"
            }
        }

        When("setting a settlement currency (YEC) without YCASH role") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("YEC" to 100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 403 with AD_YCASH_ROLE_REQUIRED") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_YCASH_ROLE_REQUIRED"
            }
        }

        When("setting a settlement currency (YEC) with YCASH role") {
            userRoleRepository.add(userId, Role.YCASH)
            val headersWithYcash = authHeaders(userId, "USER", "PIRATE", "YCASH")
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("YEC" to 100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headersWithYcash)

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val updatedAd = adService.getAd(ad.id)
                updatedAd?.fixedPrices?.keys shouldBe setOf(Currency.YEC)
            }
        }
    }

    Given("conflicting field validation for FIXED_CRYPTO mode") {
        val userId = setupUser(Role.USER, Role.PIRATE)
        val headers = authHeaders(userId, "USER", "PIRATE")

        When("sending fixedPrices together with price in the same request") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf(
                "fixedPrices" to mapOf("ARRR" to 100),
                "price" to 50,
                "priceCurrency" to "USD"
            )

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_CONFLICTING_PRICE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_CONFLICTING_PRICE"
            }
        }

        When("sending fixedPrices together with settlementCurrencies in the same request") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf(
                "fixedPrices" to mapOf("ARRR" to 100),
                "settlementCurrencies" to listOf("ARRR")
            )

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_CONFLICTING_SETTLEMENT") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_CONFLICTING_SETTLEMENT"
            }
        }

        When("sending only price on an existing FIXED_CRYPTO ad (mode inferred from existing)") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            adService.updateAd(ad.id, userId.toHexString(), UpdateAdRequest(
                fixedPrices = mapOf(Currency.ARRR to BigDecimal.TEN)
            ))

            val body = mapOf("price" to 50, "priceCurrency" to "USD")
            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_CONFLICTING_PRICE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_CONFLICTING_PRICE"
            }
        }

        When("sending only settlementCurrencies on an existing FIXED_CRYPTO ad") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            adService.updateAd(ad.id, userId.toHexString(), UpdateAdRequest(
                fixedPrices = mapOf(Currency.ARRR to BigDecimal.TEN)
            ))

            val body = mapOf("settlementCurrencies" to listOf("ARRR"))
            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_CONFLICTING_SETTLEMENT") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_CONFLICTING_SETTLEMENT"
            }
        }
    }

    Given("conflicting field validation for PEGGED mode") {
        val userId = setupUser(Role.USER, Role.PIRATE)
        val headers = authHeaders(userId, "USER", "PIRATE")

        When("sending fixedPrices on an existing PEGGED ad") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            adService.updateAd(ad.id, userId.toHexString(), UpdateAdRequest(
                pricingMode = PricingMode.PEGGED
            ))

            val body = mapOf("fixedPrices" to mapOf("ARRR" to 100))
            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with PEGGED_CONFLICTING_FIXED_PRICES") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_PEGGED_CONFLICTING_FIXED_PRICES"
            }
        }

        When("explicitly switching to PEGGED while also sending fixedPrices") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf(
                "pricingMode" to "PEGGED",
                "fixedPrices" to mapOf("ARRR" to 100)
            )

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with PEGGED_CONFLICTING_FIXED_PRICES") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_PEGGED_CONFLICTING_FIXED_PRICES"
            }
        }
    }

    Given("fixedPrices value validation") {
        val userId = setupUser(Role.USER, Role.PIRATE)
        val headers = authHeaders(userId, "USER", "PIRATE")

        When("setting zero price in fixedPrices") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("ARRR" to 0))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_INVALID_PRICE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_INVALID_PRICE"
            }
        }

        When("setting negative price in fixedPrices") {
            val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)
            val body = mapOf("fixedPrices" to mapOf("ARRR" to -100))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with FIXED_CRYPTO_INVALID_PRICE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_FIXED_CRYPTO_INVALID_PRICE"
            }
        }
    }
})
