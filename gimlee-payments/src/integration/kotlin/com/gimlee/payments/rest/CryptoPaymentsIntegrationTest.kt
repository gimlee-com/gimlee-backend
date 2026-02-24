package com.gimlee.payments.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.payments.crypto.web.dto.AddViewKeyRequest
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class CryptoPaymentsIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper
) : BaseIntegrationTest({

    val userId = "user123"
    val principal = Principal(userId = userId, username = "testuser", roles = listOf(Role.USER))

    val invalidPirateResponse = """
        {
          "result": null,
          "error": {
            "code": -5,
            "message": "Invalid viewing key"
          },
          "id": "1"
        }
    """.trimIndent()

    val invalidYcashResponse = """
        {
          "result": null,
          "error": {
            "code": -5,
            "message": "Invalid viewing key"
          },
          "id": "1"
        }
    """.trimIndent()

    val unsupportedPirateAddressTypeResponse = """
        {
          "result": {
            "type": "sprout",
            "address": "zs1piratesprout"
          },
          "error": null,
          "id": "1"
        }
    """.trimIndent()

    val unsupportedYcashAddressTypeResponse = """
        {
          "result": {
            "type": "orchard",
            "address": "ys1ycashorchard"
          },
          "error": null,
          "id": "1"
        }
    """.trimIndent()

    Given("A user trying to add an invalid Pirate Chain viewing key") {
        wireMockServer.stubFor(
            post(urlEqualTo("/pirate"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(invalidPirateResponse)
                )
        )

        When("The request is made") {
            val result = mockMvc.post("/payments/piratechain/addresses/view-key") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AddViewKeyRequest(viewKey = "invalid-key"))
                requestAttr("principal", principal)
            }

            Then("It should return 400 Bad Request with proper status code") {
                result.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PAYMENT_INVALID_VIEWING_KEY") }
                }
            }
        }
    }

    Given("A user trying to add an invalid Ycash viewing key") {
        wireMockServer.stubFor(
            post(urlEqualTo("/ycash"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(invalidYcashResponse)
                )
        )

        When("The request is made") {
            val result = mockMvc.post("/payments/ycash/addresses/view-key") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AddViewKeyRequest(viewKey = "invalid-key"))
                requestAttr("principal", principal)
            }

            Then("It should return 400 Bad Request with proper status code") {
                result.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PAYMENT_INVALID_VIEWING_KEY") }
                }
            }
        }
    }

    Given("A user trying to add a Pirate Chain viewing key with unsupported address type") {
        wireMockServer.stubFor(
            post(urlEqualTo("/pirate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(unsupportedPirateAddressTypeResponse)
                )
        )

        When("The request is made") {
            val result = mockMvc.post("/payments/piratechain/addresses/view-key") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AddViewKeyRequest(viewKey = "invalid-type-key"))
                requestAttr("principal", principal)
            }

            Then("It should reject unsupported address type") {
                result.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PAYMENT_UNSUPPORTED_VIEWING_KEY_ADDRESS_TYPE") }
                    jsonPath("$.message") {
                        value("""Imported viewing key address type "sprout" is not supported for ARRR. Supported types: orchard, sapling.""")
                    }
                }
            }
        }
    }

    Given("A user trying to add a Ycash viewing key with unsupported address type") {
        wireMockServer.stubFor(
            post(urlEqualTo("/ycash"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(unsupportedYcashAddressTypeResponse)
                )
        )

        When("The request is made") {
            val result = mockMvc.post("/payments/ycash/addresses/view-key") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AddViewKeyRequest(viewKey = "invalid-type-key"))
                requestAttr("principal", principal)
            }

            Then("It should reject unsupported address type") {
                result.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PAYMENT_UNSUPPORTED_VIEWING_KEY_ADDRESS_TYPE") }
                    jsonPath("$.message") {
                        value("""Imported viewing key address type "orchard" is not supported for YEC. Supported types: sapling.""")
                    }
                }
            }
        }
    }
}) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.payments.pirate-chain.client.rpc-url") { "http://localhost:${wireMockServer.port()}/pirate" }
            registry.add("gimlee.payments.ycash.client.rpc-url") { "http://localhost:${wireMockServer.port()}/ycash" }
        }
    }
}
