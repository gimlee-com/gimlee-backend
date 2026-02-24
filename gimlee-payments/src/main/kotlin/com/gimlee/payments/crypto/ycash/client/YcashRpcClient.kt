package com.gimlee.payments.crypto.ycash.client

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
import com.gimlee.payments.crypto.client.CryptoClient
import com.gimlee.payments.crypto.client.model.Address
import com.gimlee.payments.crypto.client.model.RawReceivedTransaction
import com.gimlee.payments.crypto.ycash.client.model.UnspentOutput
import com.gimlee.payments.crypto.ycash.config.YcashClientProperties
import com.gimlee.payments.crypto.ycash.client.model.ZSendManyAmount
import java.io.IOException
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class YcashRpcClient(
    private val httpClient: HttpClient,
    private val properties: YcashClientProperties,
    private val isProd: Boolean = true
) : CryptoClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        private const val NO_RESCAN = "no"

        private const val RPC_GET_INFO = "getinfo"
        private const val RPC_Z_SEND_MANY = "z_sendmany"
        private const val RPC_Z_GET_NEW_ADDRESS = "z_getnewaddress"
        private const val RPC_GET_NEW_ADDRESS = "getnewaddress"
        private const val RPC_LIST_ADDRESSES = "listaddressgroupings"
        private const val RPC_LIST_UNSPENT = "listunspent"
        private const val RPC_IMPORT_VIEWING_KEY = "z_importviewingkey"
        private const val RPC_LIST_RECEIVED_BY_ADDRESS = "z_listreceivedbyaddress"

        private const val ALREADY_CONTAINS_PRIVATE_KEY_ERROR_CODE = -4
        private const val ALREADY_CONTAINS_PRIVATE_KEY_ERROR_MSG_PART = "The wallet already contains the private key for this viewing key"
        private val ADDRESS_REGEX = Regex("address: ([a-zA-Z0-9]+)")
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
                        val errorMsg = it["message"] as? String ?: "Unknown RPC error"
                        val errorCode = it["code"] as? Int
                        throw YcashRpcException("Node Error: $errorMsg (Code: $errorCode)", errorCode, errorMsg)
                    }
                    rpcResponse
                } else {
                    // Attempt to parse JSON error even on 500 status codes (common for RPC)
                    val rpcError = try {
                        val errorType = determineResponseType<Any>(method) // Use Any/generic to just look for error field
                        val rpcResponse: RpcResponse<Any> = objectMapper.readValue(jsonResponse, errorType)
                        rpcResponse.error
                    } catch (ignore: Exception) {
                        null
                    }

                    rpcError?.let {
                        val errorMsg = it["message"] as? String ?: "Unknown RPC error"
                        val errorCode = it["code"] as? Int
                        throw YcashRpcException("Node Error: $errorMsg (Code: $errorCode)", errorCode, errorMsg)
                    }

                    val errorReason = response.reasonPhrase ?: "Unknown Error"
                    EntityUtils.consumeQuietly(entity)
                    throw IOException("HTTP Error: $statusCode $errorReason. Response: $jsonResponse")
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to execute RPC request '$method': ${e.message}", e)
        } catch (e: YcashRpcException) {
            throw e
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

    @Throws(IOException::class, YcashRpcException::class)
    override fun importViewingKey(viewKey: String): RpcResponse<Address> =
        try {
            callRpc(RPC_IMPORT_VIEWING_KEY, listOf(viewKey, NO_RESCAN))
        } catch (e: YcashRpcException) {
            if (!isProd && e.errorCode == ALREADY_CONTAINS_PRIVATE_KEY_ERROR_CODE &&
                e.errorMsg?.contains(ALREADY_CONTAINS_PRIVATE_KEY_ERROR_MSG_PART) == true
            ) {
                val address = e.errorMsg.let { msg ->
                    ADDRESS_REGEX.find(msg)?.groupValues?.get(1) ?: "unknown"
                }
                log.info("Viewing key already has a private key in the wallet for address: {}. Ignoring error as we are not in prod.", address)
                RpcResponse(result = Address(type = "sapling", address = address), error = null, id = null)
            } else {
                throw e
            }
        }

    @Throws(IOException::class, YcashRpcException::class)
    override fun getReceivedByAddress(address: String, minConfirmations: Int): RpcResponse<List<RawReceivedTransaction>> =
        callRpc(RPC_LIST_RECEIVED_BY_ADDRESS, listOf(address, minConfirmations))

    class YcashRpcException(
        message: String,
        val errorCode: Int? = null,
        val errorMsg: String? = null
    ) : Exception(message)
}
