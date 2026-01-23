package com.gimlee.api.web

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.config.UserPreferencesProperties
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.Map

class SessionInitIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val properties: UserPreferencesProperties
) : BaseIntegrationTest({

    Given("the session init endpoint") {
        val url = "/session/init?decorators=preferredCurrency"

        When("requesting preferredCurrency as a guest with pl-PL") {
            val headers = org.springframework.http.HttpHeaders()
            headers.set("Accept-Language", "pl-PL")
            val entity = org.springframework.http.HttpEntity<Unit>(headers)

            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)

            Then("it should return preferred currency as configured for pl-PL") {
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body!!
                body["preferredCurrency"] shouldBe properties.currencyMappings["pl-PL"]
            }
        }

        When("requesting preferredCurrency as a guest with en-US") {
            val headers = org.springframework.http.HttpHeaders()
            headers.set("Accept-Language", "en-US")
            val entity = org.springframework.http.HttpEntity<Unit>(headers)

            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)

            Then("it should return default preferred currency") {
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body!!
                body["preferredCurrency"] shouldBe properties.defaultCurrency
            }
        }
    }
})
