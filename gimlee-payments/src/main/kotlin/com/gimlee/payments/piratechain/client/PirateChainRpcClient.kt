package com.gimlee.payments.piratechain.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import com.gimlee.common.UUIDv7
import com.gimlee.payments.piratechain.client.model.Address
import com.gimlee.payments.piratechain.client.model.RawReceivedTransaction
import com.gimlee.payments.piratechain.client.model.RpcRequest
import com.gimlee.payments.piratechain.client.model.RpcResponse
import com.gimlee.payments.piratechain.config.PirateChainClientProperties
import java.io.IOException
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class PirateChainRpcClient(
    private val httpClient: HttpClient,
    private val properties: PirateChainClientProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        private const val NO_RESCAN = "no"

        private const val RPC_IMPORT_VIEWING_KEY = "z_importviewingkey"
        private const val RPC_LIST_RECEIVED_BY_ADDRESS = "z_listreceivedbyaddress"
    }

    @Throws(IOException::class, PirateChainRpcException::class)
    private inline fun <reified T> callRpc(method: String, params: List<Any>): RpcResponse<T> {
        val request = RpcRequest(method, params, UUIDv7.generate().toString())
        val jsonRequest = objectMapper.writeValueAsString(request)

        val postRequest = HttpPost(properties.rpcUrl).apply {
            setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
            entity = StringEntity(jsonRequest, ContentType.APPLICATION_JSON)
        }

        try {
            return httpClient.execute(postRequest) { response ->
                val statusCode = response.code
                val entity = response.entity
                val jsonResponse = entity?.let { EntityUtils.toString(it) }

                log.debug("Raw JSON response for method '{}': {}", method, jsonResponse)

                if (statusCode in 200..299 && jsonResponse != null) {
                    val responseType: JavaType = determineResponseType<T>(method)
                    val rpcResponse: RpcResponse<T> = objectMapper.readValue(jsonResponse, responseType)

                    rpcResponse.result?.let {
                        log.debug("Deserialized result type for method '{}': {}", method, it::class.java.name)
                        if (it is List<*> && it.isNotEmpty()) {
                           log.debug("Deserialized list element type: {}", it.first()!!::class.java.name)
                        }
                    }

                    rpcResponse.error?.let {
                        throw PirateChainRpcException("RPC Error: ${it["message"]} (Code: ${it["code"]})")
                    }
                    rpcResponse
                } else {
                    val errorReason = response.reasonPhrase ?: "Unknown Error"
                    EntityUtils.consumeQuietly(entity)
                    throw IOException("HTTP Error: $statusCode $errorReason. Response: $jsonResponse")
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to execute RPC request '$method': ${e.message}", e)
        } catch (e: Exception) {
             log.error("Unexpected error during RPC call processing for method '{}': {}", method, e.message, e)
            throw PirateChainRpcException("Failed to process RPC response for method '$method': ${e.message}")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private inline fun <reified T> determineResponseType(method: String): JavaType {
        val typeFactory = objectMapper.typeFactory
        val typeT: Type = typeOf<T>().javaType
        val javaTypeT: JavaType = typeFactory.constructType(typeT)
        val responseType: JavaType = typeFactory.constructParametricType(
            RpcResponse::class.java,
            javaTypeT
        )

        log.debug("Determined JavaType for method '{}': {}", method, responseType.toCanonical())
        return responseType
    }

    @Throws(IOException::class, PirateChainRpcException::class)
    fun importViewingKey(viewKey: String): RpcResponse<Address> =
        callRpc(RPC_IMPORT_VIEWING_KEY, listOf(viewKey, NO_RESCAN))

    @Throws(IOException::class, PirateChainRpcException::class)
    fun getReceivedByAddress(address: String, minConfirmations: Int): RpcResponse<List<RawReceivedTransaction>> =
        callRpc(RPC_LIST_RECEIVED_BY_ADDRESS, listOf(address, minConfirmations))

    class PirateChainRpcException(message: String) : Exception(message)
}