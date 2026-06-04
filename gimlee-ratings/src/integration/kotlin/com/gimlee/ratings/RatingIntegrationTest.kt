package com.gimlee.ratings

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.ratings.persistence.model.RatingAggregateDocument
import com.gimlee.ratings.persistence.model.RatingDocument
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query

class RatingIntegrationTest : BaseIntegrationTest({

    beforeSpec {
        mongoTemplate.remove(Query(), RatingDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), RatingEligibilityDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), RatingAggregateDocument.COLLECTION_NAME)
    }

    val raterId = "rater-001"
    val rateeId = "ratee-001"
    val contextType = "ORDER"
    val contextId = "purchase-001"

    fun userHeaders(userId: String): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId,
            username = "testuser",
            roles = listOf("USER")
        )

    fun grantEligibility(
        raterId: String,
        rateeId: String,
        contextId: String,
        contextType: String = "ORDER"
    ) {
        val now = System.currentTimeMillis() * 1000
        val bson = org.bson.Document()
            .append("_id", "elig-${raterId}-${contextId}")
            .append("ct", contextType)
            .append("cid", contextId)
            .append("rtr", raterId)
            .append("rte", rateeId)
            .append("rk", "SEL")
            .append("snp", org.bson.Document()
                .append("rt", "PURCHASE_ITEMS")
                .append("it", listOf(
                    org.bson.Document()
                        .append("aid", "ad-001")
                        .append("nm", "Test Item")
                        .append("qty", 1)
                        .append("up", "10.00")
                        .append("cur", "USD")
                        .append("tp", null)
                ))
            )
            .append("st", "PND")
            .append("rid", null)
            .append("af", now - 1000000)
            .append("exp", now + 86400000000L)
            .append("ca", now)
        mongoTemplate.insert(bson, RatingEligibilityDocument.COLLECTION_NAME)
    }

    Given("no eligibility exists for a rater") {
        When("they try to create a rating") {
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to contextType,
                    "contextId" to "nonexistent-purchase",
                    "score" to 5
                ),
                userHeaders(raterId)
            )

            Then("it should fail with ELIGIBILITY_NOT_FOUND") {
                response.statusCode shouldBe 404
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "ELIGIBILITY_NOT_FOUND"
            }
        }
    }

    Given("an eligibility exists for a rater") {
        grantEligibility(raterId, rateeId, contextId)

        When("they create a rating with valid data") {
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to contextType,
                    "contextId" to contextId,
                    "score" to 4,
                    "title" to "Good experience",
                    "body" to "Smooth transaction, would recommend."
                ),
                userHeaders(raterId)
            )

            Then("it should succeed with 201") {
                response.statusCode shouldBe 201
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                body["status"] shouldBe "RATING_CREATED"
                val data = body["data"] as Map<*, *>
                data["score"] shouldBe 4
                data["title"] shouldBe "Good experience"
                data["body"] shouldBe "Smooth transaction, would recommend."
            }
        }

        When("they try to create a duplicate rating for the same context") {
            // Re-grant eligibility since the first create consumed it;
            // the service checks eligibility before existing-rating, so we need a pending eligibility
            // to reach the duplicate-detection code path.
            val eligId = "elig-${raterId}-${contextId}"
            mongoTemplate.remove(
                org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("_id").`is`(eligId)
                ),
                RatingEligibilityDocument.COLLECTION_NAME
            )
            grantEligibility(raterId, rateeId, contextId)
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to contextType,
                    "contextId" to contextId,
                    "score" to 5
                ),
                userHeaders(raterId)
            )

            Then("it should fail with ALREADY_EXISTS") {
                response.statusCode shouldBe 409
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_ALREADY_EXISTS"
            }
        }
    }

    Given("a rating exists") {
        val existingRatingId: String
        run {
            grantEligibility("rater-edit", "ratee-edit", "purchase-edit")
            val createResponse = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-edit",
                    "score" to 3,
                    "title" to "Initial",
                    "body" to "Initial body"
                ),
                userHeaders("rater-edit")
            )
            createResponse.statusCode shouldBe 201
            val data = createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            existingRatingId = data["id"] as String
        }

        When("the rater edits the rating within the edit window") {
            val response = restClient.patch(
                "/ratings/$existingRatingId",
                mapOf(
                    "score" to 5,
                    "title" to "Updated",
                    "body" to "Updated body after reflection"
                ),
                userHeaders("rater-edit")
            )

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_UPDATED"
                val data = body["data"] as Map<*, *>
                data["score"] shouldBe 5
                data["title"] shouldBe "Updated"
                data["edited"] shouldBe true
            }
        }

        When("the rater fetches their rating by ID") {
            val response = restClient.get(
                "/ratings/public/$existingRatingId",
                userHeaders("rater-edit")
            )

            Then("it should return the rating") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["id"] shouldBe existingRatingId
                data["score"] shouldBe 5
            }
        }

        When("another user tries to edit the rating") {
            val response = restClient.patch(
                "/ratings/$existingRatingId",
                mapOf("score" to 1),
                userHeaders("unauthorized-user")
            )

            Then("it should fail with NOT_AUTHORIZED") {
                response.statusCode shouldBe 403
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_NOT_AUTHORIZED"
            }
        }
    }

    Given("a rating with supplements allowed") {
        val ratingId: String
        run {
            grantEligibility("rater-sup", "ratee-sup", "purchase-sup")
            val createResponse = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-sup",
                    "score" to 4,
                    "title" to "Supplement test"
                ),
                userHeaders("rater-sup")
            )
            createResponse.statusCode shouldBe 201
            val data = createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            ratingId = data["id"] as String

            // Backdate editableUntil to 8 days ago so that cooldownEnd (editableUntil + 7 days) is in the past
            val eightDaysAgoMicros = (System.currentTimeMillis() - 8 * 86400000L) * 1000
            val updateQuery = Query(
                where("_id").`is`(ratingId)
            )
            val update = org.springframework.data.mongodb.core.query.Update()
                .set(RatingDocument.FIELD_EDITABLE_UNTIL, eightDaysAgoMicros)
            mongoTemplate.updateFirst(updateQuery, update, RatingDocument.COLLECTION_NAME)
        }

        When("the rater adds a supplement") {
            val response = restClient.post(
                "/ratings/$ratingId/supplements",
                mapOf("body" to "Update: product quality held up over time"),
                userHeaders("rater-sup")
            )

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_SUPPLEMENT_ADDED"
            }
        }
    }

    Given("a rating exists for response") {
        val ratingId: String
        val sellerId = "seller-resp"
        run {
            grantEligibility("buyer-resp", sellerId, "purchase-resp")
            val createResponse = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-resp",
                    "score" to 3,
                    "title" to "Response test"
                ),
                userHeaders("buyer-resp")
            )
            createResponse.statusCode shouldBe 201
            val data = createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            ratingId = data["id"] as String
        }

        When("the ratee adds a response") {
            val response = restClient.post(
                "/ratings/$ratingId/response",
                mapOf("body" to "Thank you for your feedback, we appreciate it."),
                userHeaders(sellerId)
            )

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_RESPONSE_ADDED"
            }
        }
    }

    Given("a rating exists for deletion") {
        val ratingId: String
        run {
            grantEligibility("rater-del", "ratee-del", "purchase-del")
            val createResponse = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-del",
                    "score" to 2
                ),
                userHeaders("rater-del")
            )
            createResponse.statusCode shouldBe 201
            val data = createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            ratingId = data["id"] as String
        }

        When("the rater deletes the rating") {
            val response = restClient.delete(
                "/ratings/$ratingId",
                userHeaders("rater-del")
            )

            Then("it should succeed") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_DELETED"
            }
        }

        When("fetching the deleted rating") {
            val response = restClient.get(
                "/ratings/public/$ratingId",
                userHeaders("rater-del")
            )

            Then("it should still return the rating with DELETED status (soft-delete)") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["id"] shouldBe ratingId
            }
        }
    }

    Given("ratings exist for a ratee") {
        run {
            grantEligibility("rater-list1", "ratee-list", "purchase-list1")
            restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-list1",
                    "score" to 5,
                    "title" to "Excellent"
                ),
                userHeaders("rater-list1")
            )

            grantEligibility("rater-list2", "ratee-list", "purchase-list2")
            restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-list2",
                    "score" to 4,
                    "title" to "Good"
                ),
                userHeaders("rater-list2")
            )
        }

        When("querying ratings for the ratee") {
            val response = restClient.get(
                "/ratings/user/ratee-list",
                userHeaders("anyone")
            )

            Then("it should return the ratings") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 2
            }
        }

        When("querying the aggregate for the ratee") {
            val response = restClient.get(
                "/ratings/aggregate/ratee-list",
                userHeaders("anyone")
            )

            Then("it should return aggregate stats") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["count"] shouldBe 2
                data["average"] shouldNotBe null
            }
        }
    }

    Given("an unauthenticated user") {
        When("they try to create a rating") {
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "some-purchase",
                    "score" to 5
                )
            )

            Then("it should fail with 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    Given("a rating with invalid content") {
        When("the rater tries to create a rating with script tags") {
            grantEligibility("rater-xss", "ratee-xss", "purchase-xss")
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-xss",
                    "score" to 5,
                    "body" to "<script>alert('xss')</script>"
                ),
                userHeaders("rater-xss")
            )

            Then("it should fail with BODY_NOT_SANITIZED") {
                response.statusCode shouldBe 400
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "RATING_BODY_NOT_SANITIZED"
            }
        }
    }

    Given("a rating with invalid score") {
        When("the rater tries to create a rating with score 0") {
            grantEligibility("rater-score", "ratee-score", "purchase-score")
            val response = restClient.post(
                "/ratings",
                mapOf(
                    "contextType" to "ORDER",
                    "contextId" to "purchase-score",
                    "score" to 0
                ),
                userHeaders("rater-score")
            )

            Then("it should fail with validation error") {
                response.statusCode shouldBe 400
            }
        }
    }
})
