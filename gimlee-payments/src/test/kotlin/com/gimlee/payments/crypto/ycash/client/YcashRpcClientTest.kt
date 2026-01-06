package com.gimlee.payments.crypto.ycash.client

import com.gimlee.payments.crypto.ycash.config.YcashClientProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import java.io.IOException

class YcashRpcClientTest : StringSpec({
    val httpClient = mockk<HttpClient>()
    val properties = YcashClientProperties(
        rpcUrl = "http://localhost:8232",
        user = "user",
        password = "password",
        maxConnections = 10,
        connectionRequestTimeoutMillis = 1000,
        responseTimeoutMillis = 1000
    )
    
    "importViewingKey should throw IOException when RPC returns 500 error and isProd is true" {
        val client = YcashRpcClient(httpClient, properties, isProd = true)
        val jsonResponse = """{"result":null,"error":{"code":-4,"message":"The wallet already contains the private key for this viewing key (address: yregtestsapling1z9n9ktydpwljq3cur50g2uusutt5v2v023vl2sx8p4938zmr0xr6nzeslc3r6y92zkldwrfckjl)"},"id":"019b94d0-0876-7f15-978f-36cb53d03671"}"""

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 500
        every { response.entity } returns StringEntity(jsonResponse)
        every { response.reasonPhrase } returns "Internal Server Error"
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        // Now it throws YcashRpcException because we fixed the bug in callRpc
        val exception = shouldThrow<YcashRpcClient.YcashRpcException> {
            client.importViewingKey("view-key")
        }
        
        exception.message shouldBe "Node Error: The wallet already contains the private key for this viewing key (address: yregtestsapling1z9n9ktydpwljq3cur50g2uusutt5v2v023vl2sx8p4938zmr0xr6nzeslc3r6y92zkldwrfckjl) (Code: -4)"
    }

    "importViewingKey should return success when RPC returns 500 error -4 and isProd is false" {
        val client = YcashRpcClient(httpClient, properties, isProd = false)
        val address = "yregtestsapling1z9n9ktydpwljq3cur50g2uusutt5v2v023vl2sx8p4938zmr0xr6nzeslc3r6y92zkldwrfckjl"
        val jsonResponse = """{"result":null,"error":{"code":-4,"message":"The wallet already contains the private key for this viewing key (address: $address)"},"id":"019b94d0-0876-7f15-978f-36cb53d03671"}"""

        val response = mockk<ClassicHttpResponse>()
        every { response.code } returns 500
        every { response.entity } returns StringEntity(jsonResponse)
        every { response.reasonPhrase } returns "Internal Server Error"
        
        every { httpClient.execute(any(), any<HttpClientResponseHandler<Any>>()) } answers {
            val handler = secondArg<HttpClientResponseHandler<Any>>()
            handler.handleResponse(response)
        }

        val result = client.importViewingKey("view-key")
        
        result.result?.address shouldBe address
        result.error shouldBe null
    }
})
