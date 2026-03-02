package com.gimlee.api

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.Document
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class AdManagementIntegrationTest(
    private val adService: AdService,
    private val adRepository: AdRepository,
    private val purchaseService: PurchaseService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository
) : BaseIntegrationTest({

    fun setupSellerWithWallet(): ObjectId {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userWalletAddressRepository.addAddressToUser(sellerId, WalletAddressInfo(
            type = Currency.ARRR,
            addressType = WalletShieldedAddressType.SAPLING,
            zAddress = "zs1test${sellerId.toHexString().take(8)}",
            viewKeyHash = "hash", viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        ))
        return sellerId
    }

    fun createCompleteActiveAd(sellerId: ObjectId, stock: Int = 10): com.gimlee.ads.domain.model.Ad {
        val uid = sellerId.toHexString()
        val ad = adService.createAd(uid, "Complete Ad", null, stock)
        adService.updateAd(ad.id, uid, UpdateAdRequest(
            description = "A full description",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = stock
        ))
        return adService.activateAd(ad.id, uid)
    }

    fun authHeaders(userId: ObjectId): Map<String, String> {
        return restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = listOf("USER", "PIRATE")
        )
    }

    Given("editing active ads") {
        val sellerId = setupSellerWithWallet()
        val headers = authHeaders(sellerId)

        When("updating an active ad with valid complete data") {
            val ad = createCompleteActiveAd(sellerId)
            val body = mapOf("title" to "Updated Title")

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should succeed with 200") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["title"] shouldBe "Updated Title"
                responseBody["status"] shouldBe "ACTIVE"
            }
        }

        When("updating an active ad setting stock to 0") {
            val ad = createCompleteActiveAd(sellerId)
            val body = mapOf("stock" to 0)

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should be rejected with ACTIVE_AD_INCOMPLETE_UPDATE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_ACTIVE_AD_INCOMPLETE_UPDATE"
                (responseBody["message"] as String) shouldContain "incomplete state"
            }
        }

        When("updating an active ad clearing settlement currencies") {
            val ad = createCompleteActiveAd(sellerId)
            val body = mapOf("settlementCurrencies" to emptyList<String>())

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should be rejected with ACTIVE_AD_INCOMPLETE_UPDATE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_ACTIVE_AD_INCOMPLETE_UPDATE"
            }
        }

        When("updating an active ad changing only the title") {
            val ad = createCompleteActiveAd(sellerId)
            val body = mapOf("title" to "New Valid Title")

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should succeed since all required fields remain set") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["title"] shouldBe "New Valid Title"
                responseBody["status"] shouldBe "ACTIVE"
            }
        }
    }

    Given("editing inactive ads") {
        val sellerId = setupSellerWithWallet()
        val headers = authHeaders(sellerId)

        When("updating an inactive ad with incomplete data") {
            val ad = adService.createAd(sellerId.toHexString(), "Draft Ad", null, 0)
            val body = mapOf("title" to "Still Draft")

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should succeed — incomplete data is allowed for inactive ads") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["title"] shouldBe "Still Draft"
                responseBody["status"] shouldBe "INACTIVE"
            }
        }
    }

    Given("editing deleted ads") {
        val sellerId = setupSellerWithWallet()
        val headers = authHeaders(sellerId)

        When("attempting to update a deleted ad") {
            val ad = adService.createAd(sellerId.toHexString(), "To Delete", null, 5)

            // Manually set status to DELETED via MongoDB
            mongoTemplate.getCollection(AdRepository.COLLECTION_NAME).updateOne(
                Document("_id", ObjectId(ad.id)),
                Document("\$set", Document("s", "DELETED"))
            )

            val body = mapOf("title" to "Should Fail")
            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should be rejected with INVALID_AD_STATUS") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_INVALID_AD_STATUS"
            }
        }
    }

    Given("DTO validation errors") {
        val sellerId = setupSellerWithWallet()
        val headers = authHeaders(sellerId)

        When("updating an ad with price set to 0") {
            val ad = adService.createAd(sellerId.toHexString(), "Test Ad", null, 5)
            val body = mapOf("price" to 0, "priceCurrency" to "ARRR")

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with field-level validation detail") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "BAD_REQUEST"
                (responseBody["message"] as String) shouldContain "price"
                (responseBody["message"] as String) shouldContain "positive"
            }
        }

        When("updating an ad with a title exceeding max length") {
            val ad = adService.createAd(sellerId.toHexString(), "Test Ad", null, 5)
            val body = mapOf("title" to "A".repeat(101))

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should return 400 with field-level validation detail") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "BAD_REQUEST"
                (responseBody["message"] as String) shouldContain "title"
            }
        }
    }

    Given("an active ad with locked stock from a purchase") {
        val sellerId = setupSellerWithWallet()
        val headers = authHeaders(sellerId)
        val ad = createCompleteActiveAd(sellerId, stock = 10)

        val buyerId = ObjectId.get()
        purchaseService.purchase(
            buyerId,
            listOf(PurchaseItemRequestDto(ad.id, 3, BigDecimal.TEN)),
            Currency.ARRR
        )

        val adAfterPurchase = adService.getAd(ad.id)!!
        adAfterPurchase.lockedStock shouldBe 3

        When("the seller attempts to set stock below locked stock") {
            val body = mapOf("stock" to 2)

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should be rejected with STOCK_LOWER_THAN_LOCKED") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "AD_STOCK_LOWER_THAN_LOCKED"
            }
        }

        When("the seller sets stock to a valid level above locked stock") {
            val body = mapOf("stock" to 5)

            val response = restClient.put("/sales/ads/${ad.id}", body, headers)

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                (responseBody["stock"] as Number).toInt() shouldBe 5
            }
        }
    }

    Given("concurrent modification scenario") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)

        When("the ad version is bumped between read and save") {
            // Read the current document (version N)
            val staleDocument = adRepository.findById(ObjectId(ad.id))!!

            // Simulate a concurrent modification by locking stock (this also bumps version)
            adRepository.incrementLockedStock(ObjectId(ad.id), 1)

            // Attempt to save the stale document — version mismatch should be detected
            val updatedStaleDoc = staleDocument.copy(
                title = "Should Conflict",
                updatedAtMicros = Instant.now().toMicros()
            )

            Then("saving the stale document should throw AdConcurrentModificationException") {
                io.kotest.assertions.throwables.shouldThrow<AdRepository.AdConcurrentModificationException> {
                    adRepository.save(updatedStaleDoc)
                }
            }
        }
    }
})
