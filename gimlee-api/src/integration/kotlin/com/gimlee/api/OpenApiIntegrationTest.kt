package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["gimlee.auth.jwt.enabled=false"])
@AutoConfigureMockMvc
class OpenApiIntegrationTest(
    private val mockMvc: MockMvc
) : BaseIntegrationTest({

    Given("the application is running") {
        When("GET /v3/api-docs/payments") {
            Then("it should return the payments group with security info") {
                val result = mockMvc.get("/v3/api-docs/payments").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "\"bearerAuth\":[]"
                content shouldContain "/payments/{crypto}/addresses/view-key"
                content shouldContain "**Security:** `JWT (Bearer)`"
                content shouldContain "**Required Role:** `USER`"

                content shouldContain "\"401\":{\"description\":\"Unauthorized - Missing or invalid credentials\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"
                content shouldContain "\"403\":{\"description\":\"Forbidden - Insufficient privileges\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"

                content shouldContain "PAYMENT_INVALID_PAYMENT_DATA"
            }
        }

        When("GET /v3/api-docs/auth") {
            Then("it should return the auth group with unsecured endpoints") {
                val result = mockMvc.get("/v3/api-docs/auth").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "/auth/login"
                content shouldContain "**Security:** `Unsecured`"
                content shouldContain "AUTH_INCORRECT_CREDENTIALS"
            }
        }

        When("GET /v3/api-docs/marketplace") {
            Then("it should return the marketplace group with purchase outcomes") {
                val result = mockMvc.get("/v3/api-docs/marketplace").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "PURCHASE_ADS_NOT_FOUND"
                content shouldContain "PURCHASE_PRICE_MISMATCH"
                content shouldContain "AD_PIRATE_ROLE_REQUIRED"
            }
        }

        When("GET /v3/api-docs/ads") {
            Then("it should return the ads group") {
                val result = mockMvc.get("/v3/api-docs/ads").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "AD_AD_NOT_FOUND"
            }
        }

        When("GET /v3/api-docs/user") {
            Then("it should return the user group") {
                val result = mockMvc.get("/v3/api-docs/user").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "USER_MAX_ADDRESSES_REACHED"
            }
        }

        When("GET /v3/api-docs/location") {
            Then("it should return the location group") {
                val result = mockMvc.get("/v3/api-docs/location").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "LOCATION_CITY_NOT_FOUND"
            }
        }

        When("GET /v3/api-docs/media") {
            Then("it should return the media group") {
                val result = mockMvc.get("/v3/api-docs/media").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "MEDIA_FILE_NOT_FOUND"
            }
        }

        When("GET /v3/api-docs/ratings") {
            Then("it should return the ratings group with resolvable StatusResponseDto schema") {
                val result = mockMvc.get("/v3/api-docs/ratings").andReturn()

                result.response.status shouldBe 200

                val content = result.response.contentAsString

                content shouldContain "/ratings/public/{ratingId}"
                content shouldContain "\"401\":{\"description\":\"Unauthorized - Missing or invalid credentials\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"
                content shouldContain "\"403\":{\"description\":\"Forbidden - Insufficient privileges\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"
                content shouldContain "\"StatusResponseDto\":{\"type\":\"object\""
            }
        }
    }
})
