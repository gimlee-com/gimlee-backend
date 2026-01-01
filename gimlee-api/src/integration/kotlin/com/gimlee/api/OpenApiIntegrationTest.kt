package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import io.kotest.extensions.spring.SpringExtension
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["gimlee.auth.jwt.enabled=false"])
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
                
                // Check for a secured endpoint (e.g., /payments/piratechain/addresses/view-key)
                content shouldContain "/payments/piratechain/addresses/view-key"
                content shouldContain "**Security:** `JWT (Bearer)`"
                content shouldContain "**Required Role:** `USER`"
                
                // Check for an unsecured endpoint (e.g., /auth/login)
                content shouldContain "/auth/login"
                content shouldContain "**Security:** `Unsecured`"
            }
        }
    }
})
