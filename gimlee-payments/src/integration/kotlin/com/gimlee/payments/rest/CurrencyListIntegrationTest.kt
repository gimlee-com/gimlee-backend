package com.gimlee.payments.rest

import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import com.gimlee.payments.rest.dto.CurrencyDto

class CurrencyListIntegrationTest(
    private val restTemplate: TestRestTemplate
) : BaseIntegrationTest({

    Given("the currency list endpoint") {
        val url = "/payments/currency/list"

        When("requesting all currencies") {
            val response = restTemplate.getForEntity(url, Array<CurrencyDto>::class.java)

            Then("it should return all supported currencies") {
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body!!
                body.size shouldBe 6
            }
        }

        When("requesting all currencies with Polish locale") {
            val headers = org.springframework.http.HttpHeaders()
            headers.set("Accept-Language", "pl-PL")
            val entity = org.springframework.http.HttpEntity<Unit>(headers)
            
            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Array<CurrencyDto>::class.java)

            Then("it should return all supported currencies with Polish names") {
                response.statusCode shouldBe HttpStatus.OK
                val body = response.body!!
                val pln = body.find { it.code == "PLN" }!!
                pln.name shouldBe "Polski złoty"
                
                val usd = body.find { it.code == "USD" }!!
                usd.name shouldBe "Dolar amerykański"
            }
        }
    }
})
