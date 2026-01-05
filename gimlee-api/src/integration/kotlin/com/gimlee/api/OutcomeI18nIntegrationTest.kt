package com.gimlee.api

import com.gimlee.common.BaseIntegrationTest
import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.web.dto.StatusResponseDto
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

import com.gimlee.notifications.email.EmailService
import org.springframework.test.context.bean.override.mockito.MockitoBean

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = [
    "gimlee.auth.jwt.enabled=false",
    "gimlee.auth.csrf.validation.enabled=false",
    "server.servlet.context-path="
])
class OutcomeI18nIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper
) : BaseIntegrationTest({

    Given("the registration endpoint") {
        listOf(
            Triple("en-US", "Operation completed successfully.", "en"),
            Triple("pl-PL", "Operacja zakończona sukcesem.", "pl_pl"),
            Triple("pl", "Operacja zakończona sukcesem.", "pl")
        ).forEach { (locale, expectedMessage, suffix) ->
            When("a request is made with Accept-Language: $locale") {
                val registrationRequest = """
                    {
                        "username": "testuser_i18n_$suffix",
                        "email": "test_i18n_$suffix@example.com",
                        "password": "Password123!"
                    }
                """.trimIndent()

                val result = mockMvc.post("/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = registrationRequest
                    header("Accept-Language", locale)
                }.andExpect {
                    status { isCreated() }
                }.andReturn()

                val response = objectMapper.readValue(result.response.contentAsString, StatusResponseDto::class.java)

                Then("the message should be: $expectedMessage") {
                    response.message shouldBe expectedMessage
                }
            }
        }
    }
}) {
    @MockitoBean
    lateinit var emailService: EmailService
}
