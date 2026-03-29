package com.gimlee.api.web.reports

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
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
import com.gimlee.support.report.persistence.ReportRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query
import java.math.BigDecimal
import java.time.Instant

class ReportControllerIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val userRepository: UserRepository
) : BaseIntegrationTest({

    beforeSpec {
        mongoTemplate.remove(Query(), ReportRepository.COLLECTION_NAME)
        mongoTemplate.remove(Query(), "gimlee-ads-questions")
        mongoTemplate.remove(Query(), "gimlee-ads-answers")
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

    fun createActiveAd(sellerId: ObjectId): com.gimlee.ads.domain.model.Ad {
        val uid = sellerId.toHexString()
        val ad = adService.createAd(uid, "Test Ad", null, 10)
        adService.updateAd(ad.id, uid, UpdateAdRequest(
            description = "A full description",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        return adService.activateAd(ad.id, uid)
    }

    fun createUser(): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRepository.save(User(id = userId, username = "user_${userId.toHexString().take(6)}"))
        return userId
    }

    fun authHeaders(userId: ObjectId, roles: List<String> = listOf("USER")): Map<String, String> {
        return restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = roles
        )
    }

    fun askQuestion(adId: String, buyerHeaders: Map<String, String>): String {
        val response = restClient.post(
            "/qa/ads/$adId/questions",
            mapOf("text" to "Test question ${System.nanoTime()}?"),
            buyerHeaders
        )
        response.statusCode shouldBe 201
        val data = (response.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
        return data["id"] as String
    }

    fun answerQuestion(questionId: String, sellerHeaders: Map<String, String>): String {
        val response = restClient.post(
            "/qa/questions/$questionId/answers",
            mapOf("text" to "Test answer ${System.nanoTime()}."),
            sellerHeaders
        )
        response.statusCode shouldBe 201
        val data = (response.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
        return data["id"] as String
    }

    // ---------------------------------------------------------------
    // 1. Reporting a Question
    // ---------------------------------------------------------------
    Given("a question that can be reported") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val buyerId = createUser()
        val buyerHeaders = authHeaders(buyerId)
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        val questionId = askQuestion(ad.id, buyerHeaders)

        When("a user reports the question") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "SPAM",
                "description" to "This question contains spam and irrelevant promotional links."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("the report is submitted successfully") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["success"] shouldBe true
                responseBody["status"] shouldBe "REPORT_SUBMITTED"
                responseBody["message"] shouldNotBe null
            }
        }

        When("the same user reports the same question again") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "HARASSMENT",
                "description" to "Reporting again with a different reason."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected as a duplicate") {
                response.statusCode shouldBe 409
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["success"] shouldBe false
                responseBody["status"] shouldBe "ALREADY_REPORTED"
            }
        }

        When("a different user reports the same question") {
            val anotherUserId = createUser()
            val anotherHeaders = authHeaders(anotherUserId)
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "INAPPROPRIATE_CONTENT",
                "description" to "I also find this question inappropriate."
            )
            val response = restClient.post("/reports", body, anotherHeaders)

            Then("the report is accepted (different reporter)") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "REPORT_SUBMITTED"
            }
        }
    }

    // ---------------------------------------------------------------
    // 2. Reporting an Answer
    // ---------------------------------------------------------------
    Given("an answer that can be reported") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val buyerId = createUser()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        val questionId = askQuestion(ad.id, buyerHeaders)
        val answerId = answerQuestion(questionId, sellerHeaders)

        When("a user reports the answer") {
            val body = mapOf(
                "targetType" to "ANSWER",
                "targetId" to answerId,
                "reason" to "SPAM",
                "description" to "This answer is misleading."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("the report is submitted successfully") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["success"] shouldBe true
                responseBody["status"] shouldBe "REPORT_SUBMITTED"
            }
        }

        When("the same user reports the same answer again") {
            val body = mapOf(
                "targetType" to "ANSWER",
                "targetId" to answerId,
                "reason" to "HARASSMENT",
                "description" to "Trying to report again."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected as a duplicate") {
                response.statusCode shouldBe 409
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "ALREADY_REPORTED"
            }
        }
    }

    // ---------------------------------------------------------------
    // 3. Reporting a non-existent target
    // ---------------------------------------------------------------
    Given("a non-existent target") {
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        When("reporting a non-existent question") {
            val fakeId = ObjectId.get().toHexString()
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to fakeId,
                "reason" to "SPAM",
                "description" to "This question doesn't exist."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with REPORT_TARGET_NOT_FOUND") {
                response.statusCode shouldBe 404
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "REPORT_TARGET_NOT_FOUND"
            }
        }

        When("reporting a non-existent answer") {
            val fakeId = ObjectId.get().toHexString()
            val body = mapOf(
                "targetType" to "ANSWER",
                "targetId" to fakeId,
                "reason" to "INAPPROPRIATE_CONTENT",
                "description" to "This answer doesn't exist."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with REPORT_TARGET_NOT_FOUND") {
                response.statusCode shouldBe 404
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "REPORT_TARGET_NOT_FOUND"
            }
        }
    }

    // ---------------------------------------------------------------
    // 4. Authentication enforcement
    // ---------------------------------------------------------------
    Given("an unauthenticated request") {
        When("submitting a report without a JWT token") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to ObjectId.get().toHexString(),
                "reason" to "SPAM"
            )
            val response = restClient.post("/reports", body)

            Then("it is rejected with 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // ---------------------------------------------------------------
    // 5. Validation errors
    // ---------------------------------------------------------------
    Given("invalid report payloads") {
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        When("submitting a report without targetType") {
            val body = mapOf(
                "targetId" to ObjectId.get().toHexString(),
                "reason" to "SPAM"
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("submitting a report without targetId") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "reason" to "SPAM"
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("submitting a report without reason") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to ObjectId.get().toHexString()
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("submitting a report with an invalid reason value") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to ObjectId.get().toHexString(),
                "reason" to "INVALID_REASON"
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("submitting a report with a description exceeding 2000 characters") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to ObjectId.get().toHexString(),
                "reason" to "SPAM",
                "description" to "x".repeat(2001)
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }
    }

    // ---------------------------------------------------------------
    // 5a. Reason-target type validation
    // ---------------------------------------------------------------
    Given("a reason that does not apply to a target type") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val buyerId = createUser()
        val buyerHeaders = authHeaders(buyerId)
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        val questionId = askQuestion(ad.id, buyerHeaders)

        When("reporting a question with COUNTERFEIT (AD-only reason)") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "COUNTERFEIT",
                "description" to "Attempting counterfeit reason on a question."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with REPORT_REASON_NOT_APPLICABLE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["success"] shouldBe false
                responseBody["status"] shouldBe "REPORT_REASON_NOT_APPLICABLE"
            }
        }

        When("reporting a question with WRONG_CATEGORY (AD-only reason)") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "WRONG_CATEGORY",
                "description" to "Attempting wrong category reason on a question."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is rejected with REPORT_REASON_NOT_APPLICABLE") {
                response.statusCode shouldBe 400
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["success"] shouldBe false
                responseBody["status"] shouldBe "REPORT_REASON_NOT_APPLICABLE"
            }
        }

        When("reporting a question with SPAM (applicable to QUESTION)") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "SPAM",
                "description" to "This is spam."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is accepted") {
                response.statusCode shouldBe 200
                val responseBody = response.bodyAs<Map<String, Any>>()!!
                responseBody["status"] shouldBe "REPORT_SUBMITTED"
            }
        }
    }

    // ---------------------------------------------------------------
    // 5b. Report reasons endpoint
    // ---------------------------------------------------------------
    Given("the report reasons endpoint") {
        val userId = createUser()
        val userHeaders = authHeaders(userId)

        When("fetching all report reasons") {
            val response = restClient.get("/reports/reasons", userHeaders)

            Then("it returns all reasons with their supported targets") {
                response.statusCode shouldBe 200
                val reasons = response.bodyAs<List<Map<String, Any>>>()!!
                reasons.size shouldBe 8
            }
        }

        When("fetching reasons for AD target type") {
            val response = restClient.get("/reports/reasons?targetType=AD", userHeaders)

            Then("it returns all reasons applicable to ADs") {
                response.statusCode shouldBe 200
                val reasons = response.bodyAs<List<Map<String, Any>>>()!!
                reasons.size shouldBe 7
                val reasonNames = reasons.map { it["reason"] as String }.toSet()
                reasonNames shouldBe setOf("SPAM", "FRAUD", "INAPPROPRIATE_CONTENT", "COUNTERFEIT", "COPYRIGHT", "WRONG_CATEGORY", "OTHER")
            }
        }

        When("fetching reasons for MESSAGE target type") {
            val response = restClient.get("/reports/reasons?targetType=MESSAGE", userHeaders)

            Then("it returns only reasons applicable to messages (excludes COUNTERFEIT, WRONG_CATEGORY)") {
                response.statusCode shouldBe 200
                val reasons = response.bodyAs<List<Map<String, Any>>>()!!
                val reasonNames = reasons.map { it["reason"] as String }.toSet()
                reasonNames shouldNotBe null
                ("COUNTERFEIT" in reasonNames) shouldBe false
                ("WRONG_CATEGORY" in reasonNames) shouldBe false
            }
        }
    }

    // ---------------------------------------------------------------
    // 6. Cross-target independence
    // ---------------------------------------------------------------
    Given("a question and its answer on the same ad") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val buyerId = createUser()
        val buyerHeaders = authHeaders(buyerId)
        val sellerHeaders = authHeaders(sellerId)
        val reporterId = createUser()
        val reporterHeaders = authHeaders(reporterId)

        val questionId = askQuestion(ad.id, buyerHeaders)
        val answerId = answerQuestion(questionId, sellerHeaders)

        When("a user reports the question") {
            val body = mapOf(
                "targetType" to "QUESTION",
                "targetId" to questionId,
                "reason" to "SPAM",
                "description" to "Spam question."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("the report is accepted") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_SUBMITTED"
            }
        }

        When("the same user then reports the answer on that question") {
            val body = mapOf(
                "targetType" to "ANSWER",
                "targetId" to answerId,
                "reason" to "INAPPROPRIATE_CONTENT",
                "description" to "Misleading answer."
            )
            val response = restClient.post("/reports", body, reporterHeaders)

            Then("it is also accepted (different targets)") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_SUBMITTED"
            }
        }
    }
})
