package com.gimlee.payments.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ConversionResult
import com.gimlee.payments.exchange.domain.ExchangePriceProvider
import com.gimlee.payments.exchange.domain.ExchangePriceResult
import com.gimlee.payments.exchange.domain.ExchangeRateService
import com.gimlee.payments.persistence.ExchangeRateRepository
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@AutoConfigureMockMvc
@Import(CurrencyConversionIntegrationTest.TestConfig::class)
class CurrencyConversionIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val exchangeRateService: ExchangeRateService
) : BaseIntegrationTest({

    beforeSpec {
        exchangeRateRepository.clear()
    }

    Given("Bitmart API returns YEC/USDT price") {
        val bitmartResponse = """
            {
              "code": 1000,
              "message": "success",
              "data": [
                ["1689736680", "1.5", "1.6", "1.4", "1.55", "100", "150"],
                ["1689736620", "1.5", "1.6", "1.4", "1.52", "100", "152"],
                ["1689736560", "1.5", "1.6", "1.4", "1.51", "100", "151"],
                ["1689736500", "1.5", "1.6", "1.4", "1.50", "100", "150"],
                ["1689736440", "1.5", "1.6", "1.4", "1.49", "100", "149"],
                ["1689736380", "1.5", "1.6", "1.4", "1.48", "100", "148"],
                ["1689736320", "1.5", "1.6", "1.4", "1.47", "100", "147"],
                ["1689736260", "1.5", "1.6", "1.4", "1.46", "100", "146"],
                ["1689736200", "1.5", "1.6", "1.4", "1.45", "100", "145"],
                ["1689736140", "1.5", "1.6", "1.4", "1.44", "100", "144"]
              ]
            }
        """.trimIndent()

        wireMockServer.stubFor(
            get(urlPathEqualTo("/spot/quotation/v3/klines"))
                .withQueryParam("symbol", equalTo("YEC_USDT"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(bitmartResponse)
                )
        )

        exchangeRateService.updateRates()

        And("We check if exchange rates were fetched") {
            val resultJson = mockMvc.get("/payments/currency/convert") {
                param("amount", "1")
                param("from", "YEC")
                param("to", "USDT")
            }.andExpect {
                status { isOk() }
            }.andReturn().response.contentAsString
            
            val result = objectMapper.readValue(resultJson, ConversionResult::class.java)
            result.targetAmount.stripTrailingZeros() shouldBe BigDecimal("1.55").stripTrailingZeros()
            result.from shouldBe Currency.YEC
            result.to shouldBe Currency.USDT
        }

        When("Requesting conversion from YEC to PLN") {
            // YEC -> USDT (1.55)
            // USDT -> USD (1.0)
            // USD -> PLN (4.0)
            // Total: 1.55 * 1.0 * 4.0 = 6.2
            
            val responseJson = mockMvc.get("/payments/currency/convert") {
                param("amount", "10")
                param("from", "YEC")
                param("to", "PLN")
            }.andExpect {
                status { isOk() }
                content { contentType(MediaType.APPLICATION_JSON) }
            }.andReturn().response.contentAsString

            val response = objectMapper.readValue(responseJson, ConversionResult::class.java)

            Then("It should return the correct targetAmount and steps") {
                response.targetAmount shouldBe BigDecimal("62.00")
                response.from shouldBe Currency.YEC
                response.to shouldBe Currency.PLN
                response.steps.size shouldBe 3
                
                response.steps[0].baseCurrency shouldBe Currency.YEC
                response.steps[0].quoteCurrency shouldBe Currency.USDT
                response.steps[0].rate.stripTrailingZeros() shouldBe BigDecimal("1.55").stripTrailingZeros()
                
                response.steps[1].baseCurrency shouldBe Currency.USDT
                response.steps[1].quoteCurrency shouldBe Currency.USD
                response.steps[1].rate.stripTrailingZeros() shouldBe BigDecimal("1.0").stripTrailingZeros()
                
                response.steps[2].baseCurrency shouldBe Currency.USD
                response.steps[2].quoteCurrency shouldBe Currency.PLN
                response.steps[2].rate.stripTrailingZeros() shouldBe BigDecimal("4.0").stripTrailingZeros()
            }
        }
    }
}) {
    @TestConfiguration
    class TestConfig {
        @Bean
        fun testPriceProvider(): ExchangePriceProvider = object : ExchangePriceProvider {
            override val name: String = "TestProvider"
            private val rates = mapOf(
                (Currency.USD to Currency.PLN) to BigDecimal("4.0"),
                (Currency.USD to Currency.XAU) to BigDecimal("0.0005")
            )
            override fun supports(base: Currency, quote: Currency): Boolean = rates.containsKey(base to quote)
            override fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult? =
                rates[base to quote]?.let { ExchangePriceResult(it) }
        }
    }
}
