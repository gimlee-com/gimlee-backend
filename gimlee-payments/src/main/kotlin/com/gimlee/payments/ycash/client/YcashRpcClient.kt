package com.gimlee.payments.ycash.client

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
import com.gimlee.payments.client.model.RpcRequest
import com.gimlee.payments.client.model.RpcResponse
import com.gimlee.payments.ycash.client.model.UnspentOutput
import com.gimlee.payments.ycash.config.YcashClientProperties
import com.gimlee.payments.ycash.client.model.ZSendManyAmount
import java.io.IOException
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class YcashRpcClient(
    private val httpClient: HttpClient,
    private val properties: YcashClientProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        private const val RPC_GET_INFO = "getinfo"
        private const val RPC_Z_SEND_MANY = "z_sendmany"
        private const val RPC_Z_GET_NEW_ADDRESS = "z_getnewaddress"
        private const val RPC_GET_NEW_ADDRESS = "getnewaddress"
        private const val RPC_LIST_ADDRESSES = "listaddressgroupings"
        private const val RPC_LIST_UNSPENT = "listunspent"
    }

    @Throws(IOException::class, YcashRpcException::class)
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

                    rpcResponse.error?.let {
                        // Extract the specific message from the inner map
                        val errorMsg = it["message"] as? String ?: "Unknown RPC error"
                        val errorCode = it["code"]
                        throw YcashRpcException("Node Error: $errorMsg (Code: $errorCode)")
                    }
                    rpcResponse
                } else {
                    // Attempt to parse JSON error even on 500 status codes (common for RPC)
                    try {
                        val errorType = determineResponseType<Any>(method) // Use Any/generic to just look for error field
                        val rpcResponse: RpcResponse<Any> = objectMapper.readValue(jsonResponse, errorType)
                        rpcResponse.error?.let {
                            val errorMsg = it["message"] as? String ?: "Unknown RPC error"
                            throw YcashRpcException("Node Error: $errorMsg (Code: ${it["code"]})")
                        }
                    } catch (ignore: Exception) {
                        // If parsing fails, fall back to standard HTTP error
                    }

                    val errorReason = response.reasonPhrase ?: "Unknown Error"
                    EntityUtils.consumeQuietly(entity)
                    throw IOException("HTTP Error: $statusCode $errorReason. Response: $jsonResponse")
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to execute RPC request '$method': ${e.message}", e)
        } catch (e: Exception) {
            log.error("Unexpected error during RPC call processing for method '{}': {}", method, e.message, e)
            throw YcashRpcException("Failed to process RPC response for method '$method': ${e.message}")
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private inline fun <reified T> determineResponseType(method: String): JavaType {
        val typeFactory = objectMapper.typeFactory
        val typeT: Type = typeOf<T>().javaType
        val javaTypeT: JavaType = typeFactory.constructType(typeT)
        return typeFactory.constructParametricType(
            RpcResponse::class.java,
            javaTypeT
        )
    }

    @Throws(IOException::class, YcashRpcException::class)
    fun getInfo(): RpcResponse<Map<String, Any>> =
        callRpc(RPC_GET_INFO, emptyList())

    @Throws(IOException::class, YcashRpcException::class)
    fun zSendMany(fromAddress: String, amounts: List<ZSendManyAmount>): RpcResponse<String> =
        callRpc(RPC_Z_SEND_MANY, listOf(fromAddress, amounts))

    @Throws(IOException::class, YcashRpcException::class)
    fun zGetNewAddress(): RpcResponse<String> =
        callRpc(RPC_Z_GET_NEW_ADDRESS, emptyList())

    @Throws(IOException::class, YcashRpcException::class)
    fun getNewAddress(): RpcResponse<String> =
        callRpc(RPC_GET_NEW_ADDRESS, emptyList())

    @Throws(IOException::class, YcashRpcException::class)
    fun listAddressGroupings(): RpcResponse<List<List<List<Any>>>> =
        callRpc(RPC_LIST_ADDRESSES, emptyList())

    @Throws(IOException::class, YcashRpcException::class)
    fun listUnspent(minConfs: Int = 1, maxConfs: Int = 9999999): RpcResponse<List<UnspentOutput>> =
        callRpc(RPC_LIST_UNSPENT, listOf(minConfs, maxConfs))

    class YcashRpcException(message: String) : Exception(message)
}
