package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import java.math.BigDecimal

class MexcPriceProviderTest : StringSpec({
    val httpClient = mockk<HttpClient>()
    val objectMapper = ObjectMapper().registerKotlinModule()
    val properties = PaymentProperties(
        timeoutHours = 1
    )
    val provider = MexcPriceProvider(httpClient, objectMapper, properties)

    "fetchPrice should return price from Mexc" {
        val jsonResponse = """
            {
                "symbol": "ARRRUSDT",
                "price": "0.2527"
            }
        """.trimIndent()

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 200
        every { response.entity } returns StringEntity(jsonResponse)
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = provider.fetchPrice(Currency.ARRR, Currency.USDT)
        
        result!!.rate shouldBe BigDecimal("0.2527")
        result.isVolatile shouldBe false
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
