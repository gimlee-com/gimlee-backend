package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.ExchangeProperties
import com.gimlee.payments.config.OpenExchangeRatesProperties
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.config.VolatilityProperties
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import java.math.BigDecimal
import java.time.Instant

class OpenExchangeRatesPriceProviderTest : StringSpec({
    isolationMode = IsolationMode.InstancePerTest

    val httpClient = mockk<HttpClient>()
    val objectMapper = ObjectMapper().registerKotlinModule()
    val volatilityProperties = VolatilityProperties(
        downsideThreshold = 0.05,
        windowSeconds = 600,
        cooldownSeconds = 1800,
        stabilizationChecks = 3,
        staleThresholdSeconds = 3600
    )
    val properties = PaymentProperties(
        timeoutHours = 1,
        exchange = ExchangeProperties(
            openExchangeRates = OpenExchangeRatesProperties(
                appId = "test-app-id"
            )
        ),
        volatility = volatilityProperties
    )
    val provider = OpenExchangeRatesPriceProvider(httpClient, objectMapper, properties)

    "fetchPrice should return correct rate for USD/PLN" {
        val jsonResponse = """
            {
                "timestamp": 1600000000,
                "base": "USD",
                "rates": {
                    "USD": 1.0,
                    "PLN": 4.0,
                    "XAU": 0.0005
                }
            }
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.USD, Currency.PLN)
        
        result!!.rate.stripTrailingZeros() shouldBe BigDecimal("4").stripTrailingZeros()
        result.timestamp shouldBe Instant.ofEpochSecond(1600000000)
    }

    "fetchPrice should return correct rate for USD/XAU" {
        val jsonResponse = """
            {
                "timestamp": 1600000000,
                "base": "USD",
                "rates": {
                    "USD": 1.0,
                    "PLN": 4.0,
                    "XAU": 0.0005
                }
            }
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.USD, Currency.XAU)
        
        // 1 USD = 0.0005 XAU
        result!!.rate.stripTrailingZeros() shouldBe BigDecimal("0.0005").stripTrailingZeros()
    }

    "fetchPrice should return correct rate for XAU/USD (inverse)" {
        val jsonResponse = """
            {
                "timestamp": 1600000000,
                "base": "USD",
                "rates": {
                    "USD": 1.0,
                    "PLN": 4.0,
                    "XAU": 0.0005
                }
            }
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.XAU, Currency.USD)
        
        // 1 XAU = 1 / 0.0005 USD = 2000 USD
        result!!.rate.stripTrailingZeros() shouldBe BigDecimal("2000").stripTrailingZeros()
    }
})
