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
        When("GET /v3/api-docs") {
            Then("it should return the OpenAPI documentation with security info") {
                val result = mockMvc.get("/v3/api-docs").andReturn()
                
                result.response.status shouldBe 200

                val content = result.response.contentAsString
                
                // Check for some endpoints and their security/role info
                content shouldContain "\"bearerAuth\":[]"
                
                // Check for a secured endpoint (e.g., /payments/{crypto}/addresses/view-key)
                content shouldContain "/payments/{crypto}/addresses/view-key"
                content shouldContain "**Security:** `JWT (Bearer)`"
                content shouldContain "**Required Role:** `USER`"
                
                // Check for automatically added error responses
                content shouldContain "\"401\":{\"description\":\"Unauthorized - Missing or invalid credentials\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"
                content shouldContain "\"403\":{\"description\":\"Forbidden - Insufficient privileges\",\"content\":{\"application/json\":{\"schema\":{\"\$ref\":\"#/components/schemas/StatusResponseDto\"}}}}"

                // Check for an unsecured endpoint (e.g., /auth/login)
                content shouldContain "/auth/login"
                content shouldContain "**Security:** `Unsecured`"

                // Check for outcome status codes in documentation
                content shouldContain "PURCHASE_ADS_NOT_FOUND"
                content shouldContain "PURCHASE_PRICE_MISMATCH"
                content shouldContain "AD_AD_NOT_FOUND"
                content shouldContain "AD_PIRATE_ROLE_REQUIRED"
                content shouldContain "USER_MAX_ADDRESSES_REACHED"
                content shouldContain "PAYMENT_INVALID_PAYMENT_DATA"
                content shouldContain "AUTH_INCORRECT_CREDENTIALS"
                content shouldContain "LOCATION_CITY_NOT_FOUND"
                content shouldContain "MEDIA_FILE_NOT_FOUND"
                content shouldContain "INTERNAL_ERROR"
            }
        }
    }
})
