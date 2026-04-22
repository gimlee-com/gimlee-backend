package com.gimlee.api.web.qa

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.purchases.domain.PurchaseService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query
import java.math.BigDecimal
import java.time.Instant

class QaFacadeControllerIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val userRepository: UserRepository,
    private val purchaseService: PurchaseService
) : BaseIntegrationTest({

    beforeSpec {
        mongoTemplate.remove(Query(), "gimlee-ads-questions")
        mongoTemplate.remove(Query(), "gimlee-ads-answers")
        mongoTemplate.remove(Query(), "gimlee-ads-upvotes")
    }

    fun setupSellerWithWallet(): ObjectId {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userRepository.save(User(id = sellerId, username = "seller_${sellerId.toHexString().take(6)}"))
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
        val ad = adService.createAd(uid, "Test Ad", null, stock)
        adService.updateAd(ad.id, uid, UpdateAdRequest(
            description = "A full description",
            fixedPrices = mapOf(Currency.ARRR to BigDecimal.TEN),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = stock
        ))
        return adService.activateAd(ad.id, uid)
    }

    fun createBuyer(): ObjectId {
        val buyerId = ObjectId.get()
        userRoleRepository.add(buyerId, Role.USER)
        userRepository.save(User(id = buyerId, username = "buyer_${buyerId.toHexString().take(6)}"))
        return buyerId
    }

    fun authHeaders(userId: ObjectId, roles: List<String> = listOf("USER")): Map<String, String> {
        return restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = roles
        )
    }

    Given("Q&A on an active ad") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        When("a buyer asks a question") {
            val body = mapOf("text" to "Is this item available for international shipping?")
            val response = restClient.post("/qa/ads/${ad.id}/questions", body, buyerHeaders)

            Then("the question is created successfully") {
                response.statusCode shouldBe 201
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "QUESTION_CREATED"
                responseBody["data"] shouldNotBe null
            }
        }

        When("the seller tries to ask a question on their own ad") {
            val body = mapOf("text" to "Can I ask myself a question?")
            val response = restClient.post("/qa/ads/${ad.id}/questions", body, sellerHeaders)

            Then("it is rejected with QUESTION_OWN_AD") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "QUESTION_OWN_AD"
            }
        }

        When("asking a question on a non-existent ad") {
            val body = mapOf("text" to "Does this ad exist?")
            val fakeAdId = ObjectId.get().toHexString()
            val response = restClient.post("/qa/ads/$fakeAdId/questions", body, buyerHeaders)

            Then("it is rejected with QUESTION_AD_NOT_FOUND") {
                response.statusCode shouldBe 404
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "QUESTION_AD_NOT_FOUND"
            }
        }
    }

    Given("a question that needs answering") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        val askResponse = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "What is the warranty on this item?"),
            buyerHeaders
        )
        askResponse.statusCode shouldBe 201
        val questionData = (askResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
        val questionId = questionData["id"] as String

        When("the seller submits an answer") {
            val body = mapOf("text" to "We offer a 2-year warranty on all items.")
            val response = restClient.post("/qa/questions/$questionId/answers", body, sellerHeaders)

            Then("the answer is created and question becomes ANSWERED") {
                response.statusCode shouldBe 201
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ANSWER_CREATED"
            }
        }

        When("the seller tries to answer the same question again") {
            val body = mapOf("text" to "Duplicate answer attempt")
            val response = restClient.post("/qa/questions/$questionId/answers", body, sellerHeaders)

            Then("it is rejected with ANSWER_ALREADY_EXISTS") {
                response.statusCode shouldBe 409
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ANSWER_ALREADY_EXISTS"
            }
        }

        When("a non-buyer tries to submit a community answer") {
            val randomUser = createBuyer()
            val randomHeaders = authHeaders(randomUser)
            val body = mapOf("text" to "I can confirm this is great!")
            val response = restClient.post("/qa/questions/$questionId/answers", body, randomHeaders)

            Then("it is rejected with ANSWER_NOT_PREVIOUS_BUYER") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ANSWER_NOT_PREVIOUS_BUYER"
            }
        }
    }

    Given("answered questions on an ad") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        // Ask and answer 2 questions
        for (i in 1..2) {
            val askResp = restClient.post(
                "/qa/ads/${ad.id}/questions",
                mapOf("text" to "Question $i about the product?"),
                buyerHeaders
            )
            askResp.statusCode shouldBe 201
            val qId = ((askResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)
            val ansResp = restClient.post(
                "/qa/questions/$qId/answers",
                mapOf("text" to "Answer to question $i."),
                sellerHeaders
            )
            ansResp.statusCode shouldBe 201
        }

        When("fetching public questions (unauthenticated)") {
            val response = restClient.get("/qa/ads/${ad.id}/questions?page=0&size=10&sort=UPVOTES")

            Then("it returns answered questions") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                val content = responseBody["content"] as List<*>
                content.size shouldBe 2
            }
        }

        When("fetching Q&A stats") {
            val response = restClient.get("/qa/ads/${ad.id}/questions/stats")

            Then("it returns correct stats") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                (responseBody["totalAnswered"] as Number).toInt() shouldBe 2
            }
        }
    }

    Given("upvote and pin operations") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        val askResp = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "Question about upvotes and pins?"),
            buyerHeaders
        )
        askResp.statusCode shouldBe 201
        val questionId = ((askResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)

        // Seller answers so question becomes ANSWERED
        val ansResp = restClient.post(
            "/qa/questions/$questionId/answers",
            mapOf("text" to "Here is the answer."),
            sellerHeaders
        )
        ansResp.statusCode shouldBe 201

        When("a user upvotes a question") {
            val response = restClient.post("/qa/questions/$questionId/upvote", null, buyerHeaders)

            Then("the upvote is toggled") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "UPVOTE_TOGGLED"
            }
        }

        When("a user upvotes the same question again (toggle off)") {
            val response = restClient.post("/qa/questions/$questionId/upvote", null, buyerHeaders)

            Then("the upvote is removed") {
                response.statusCode shouldBe 200
            }
        }

        When("the seller pins a question") {
            val response = restClient.put("/qa/questions/$questionId/pin", null, sellerHeaders)

            Then("the pin is toggled") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "PIN_TOGGLED"
            }
        }

        When("a non-seller tries to pin") {
            val response = restClient.put("/qa/questions/$questionId/pin", null, buyerHeaders)

            Then("it is rejected with NOT_AD_OWNER") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "NOT_AD_OWNER"
            }
        }
    }

    Given("seller moderation capabilities") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        val askResp = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "Question that will be hidden?"),
            buyerHeaders
        )
        askResp.statusCode shouldBe 201
        val questionId = ((askResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)

        When("the seller hides a question") {
            val response = restClient.put("/qa/questions/$questionId/hide", null, sellerHeaders)

            Then("the question is hidden") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "QUESTION_HIDDEN"
            }
        }

        When("a non-seller tries to hide") {
            val askResp2 = restClient.post(
                "/qa/ads/${ad.id}/questions",
                mapOf("text" to "Another question to test hide auth?"),
                buyerHeaders
            )
            askResp2.statusCode shouldBe 201
            val qId2 = ((askResp2.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)

            val response = restClient.put("/qa/questions/$qId2/hide", null, buyerHeaders)

            Then("it is rejected with NOT_AD_OWNER") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "NOT_AD_OWNER"
            }
        }
    }

    Given("seller and buyer question views") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        // Ask a question (pending/unanswered)
        val askResp = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "Pending question for seller view?"),
            buyerHeaders
        )
        askResp.statusCode shouldBe 201

        When("the seller views unanswered questions") {
            val response = restClient.get("/qa/ads/${ad.id}/questions/seller?page=0&size=10", sellerHeaders)

            Then("it returns unanswered questions") {
                response.statusCode shouldBe 200
            }
        }

        When("a non-seller tries to view seller's unanswered questions") {
            val response = restClient.get("/qa/ads/${ad.id}/questions/seller?page=0&size=10", buyerHeaders)

            Then("it is rejected with NOT_AD_OWNER") {
                response.statusCode shouldBe 403
            }
        }

        When("the buyer views their own unanswered questions") {
            val response = restClient.get("/qa/ads/${ad.id}/questions/mine", buyerHeaders)

            Then("it returns the buyer's pending questions") {
                response.statusCode shouldBe 200
            }
        }
    }

    Given("answer editing") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)

        val askResp = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "Question for edit test?"),
            buyerHeaders
        )
        askResp.statusCode shouldBe 201
        val questionId = ((askResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)

        val ansResp = restClient.post(
            "/qa/questions/$questionId/answers",
            mapOf("text" to "Original answer text."),
            sellerHeaders
        )
        ansResp.statusCode shouldBe 201
        val answerData = (ansResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
        val answerId = answerData["id"] as String

        When("the answer author edits the answer") {
            val response = restClient.put(
                "/qa/answers/$answerId",
                mapOf("text" to "Updated answer text with more details."),
                sellerHeaders
            )

            Then("the answer is updated") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ANSWER_UPDATED"
            }
        }

        When("a non-author tries to edit the answer") {
            val response = restClient.put(
                "/qa/answers/$answerId",
                mapOf("text" to "Unauthorized edit attempt."),
                buyerHeaders
            )

            Then("it is rejected with ANSWER_NOT_OWNER") {
                response.statusCode shouldBe 403
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ANSWER_NOT_OWNER"
            }
        }
    }

    Given("admin question removal") {
        val sellerId = setupSellerWithWallet()
        val ad = createCompleteActiveAd(sellerId)
        val buyerId = createBuyer()
        val buyerHeaders = authHeaders(buyerId)
        val adminHeaders = authHeaders(ObjectId.get(), listOf("USER", "ADMIN"))

        val askResp = restClient.post(
            "/qa/ads/${ad.id}/questions",
            mapOf("text" to "Question to be removed by admin?"),
            buyerHeaders
        )
        askResp.statusCode shouldBe 201
        val questionId = ((askResp.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String)

        When("an admin removes a question") {
            val response = restClient.delete("/qa/questions/$questionId", adminHeaders)

            Then("the question is removed") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "QUESTION_REMOVED"
            }
        }
    }
})
