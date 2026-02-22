package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.config.VolatilityProperties
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import java.math.BigDecimal

class BitmartPriceProviderTest : StringSpec({
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
        volatility = volatilityProperties
    )
    val provider = BitmartPriceProvider(httpClient, objectMapper, properties)

    "fetchPrice should detect volatility when price swing is >= 5%" {
        val jsonResponse = """
            {
                "code": 1000,
                "message": "success",
                "data": [
                    ["1600000000", "100", "105", "100", "105", "1", "100"],
                    ["1600000060", "105", "110", "105", "110", "1", "110"],
                    ["1600000120", "110", "110", "100", "100", "1", "100"]
                ]
            }
        """.trimIndent()
        // max high = 110, min low = 100. swing = (110-100)/100 = 0.1 (10%) >= 5%

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.YEC, Currency.USDT)
        
        result!!.rate.stripTrailingZeros() shouldBe BigDecimal("105").stripTrailingZeros()
        result.isVolatile shouldBe true
    }

    "fetchPrice should not detect volatility when price swing is < 5%" {
        val jsonResponse = """
            {
                "code": 1000,
                "message": "success",
                "data": [
                    ["1600000000", "100", "102", "100", "102", "1", "100"],
                    ["1600000060", "102", "104", "102", "104", "1", "110"],
                    ["1600000120", "104", "104", "101", "101", "1", "100"]
                ]
            }
        """.trimIndent()
        // max high = 104, min low = 100. swing = (104-100)/100 = 0.04 (4%) < 5%

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.YEC, Currency.USDT)
        
        result!!.rate.stripTrailingZeros() shouldBe BigDecimal("102").stripTrailingZeros()
        result.isVolatile shouldBe false
    }
})
