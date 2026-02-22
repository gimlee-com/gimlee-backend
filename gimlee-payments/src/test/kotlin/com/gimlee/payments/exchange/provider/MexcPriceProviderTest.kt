package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.ExchangeProperties
import com.gimlee.payments.config.MexcProperties
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.config.VolatilityProperties
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import java.math.BigDecimal

class MexcPriceProviderTest : StringSpec({
    val httpClient = mockk<HttpClient>()
    val objectMapper = ObjectMapper().registerKotlinModule()
    
    // Setup properties with explicit values to be sure
    val mexcProperties = MexcProperties(
        baseUrl = "https://api.mexc.com",
        volatilityThreshold = 0.05,
        volatilityKlinesLimit = 5,
        volatilityKlinesInterval = "60m"
    )
    val exchangeProperties = ExchangeProperties(mexc = mexcProperties)
    val volatilityProperties = VolatilityProperties(
        downsideThreshold = 0.05,
        windowSeconds = 600,
        cooldownSeconds = 1800,
        stabilizationChecks = 3,
        staleThresholdSeconds = 3600
    )
    val properties = PaymentProperties(
        timeoutHours = 1,
        exchange = exchangeProperties,
        volatility = volatilityProperties
    )
    
    val provider = MexcPriceProvider(httpClient, objectMapper, properties)

    "fetchPrice should return price from Mexc klines" {
        // [Open time, Open, High, Low, Close, Volume, Close time, ...]
        val jsonResponse = """
            [
                [1640804880000, "0.25", "0.255", "0.248", "0.25", "1000", 1640804940000, "250"],
                [1640804940000, "0.25", "0.255", "0.248", "0.2527", "1000", 1640805000000, "250"]
            ]
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        val requestSlot = slot<HttpGet>()
        every { httpClient.execute(capture(requestSlot), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.ARRR, Currency.USDT)
        
        result!!.rate shouldBe BigDecimal("0.2527")
        result.isVolatile shouldBe false
        
        // Verify URL
        requestSlot.captured.uri.toString() shouldBe "https://api.mexc.com/api/v3/klines?symbol=ARRRUSDT&interval=60m&limit=5"
    }

    "fetchPrice should detect volatility" {
        // High volatility: High 0.30, Low 0.20 -> Swing 0.50 (50%) > 0.05
        val jsonResponse = """
            [
                [1640804880000, "0.25", "0.30", "0.20", "0.25", "1000", 1640804940000, "250"],
                [1640804940000, "0.25", "0.28", "0.22", "0.26", "1000", 1640805000000, "250"]
            ]
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        val requestSlot = slot<HttpGet>()
        every { httpClient.execute(capture(requestSlot), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.ARRR, Currency.USDT)
        
        result!!.rate shouldBe BigDecimal("0.26")
        result.isVolatile shouldBe true
    }

    "fetchPrice should return null on error" {
        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 500
        every { response.entity } returns StringEntity("Internal Server Error")
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.ARRR, Currency.USDT)
        
        result shouldBe null
    }
})
