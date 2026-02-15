package com.gimlee.common.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.hc.client5.http.classic.methods.*
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.TimeUnit

data class RestResponse(
    val statusCode: Int,
    val body: String?,
    val objectMapper: ObjectMapper
) {
    fun <T : Any> bodyAs(clazz: java.lang.Class<T>): T? {
        return body?.let { objectMapper.readValue(it, clazz) }
    }

    inline fun <reified T : Any> bodyAs(): T? {
        return body?.let { objectMapper.readValue<T>(it) }
    }

    val isSuccessful: Boolean get() = statusCode in 200..299
}

class RestTestClient(
    private val baseUrl: String,
    val objectMapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
) : Closeable {

    private val log = LoggerFactory.getLogger(javaClass)

    private val connectionManager = PoolingHttpClientConnectionManager().apply {
        maxTotal = 50
        defaultMaxPerRoute = 50
    }

    private val httpClient: CloseableHttpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .evictIdleConnections(TimeValue.of(60, TimeUnit.SECONDS))
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .setConnectionRequestTimeout(Timeout.of(2, TimeUnit.SECONDS))
                .build()
        )
        .build()

    fun get(path: String, headers: Map<String, String> = emptyMap()): RestResponse {
        return execute(HttpGet(buildUrl(path)), headers)
    }

    fun post(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()): RestResponse {
        val request = HttpPost(buildUrl(path))
        body?.let {
            request.entity = StringEntity(objectMapper.writeValueAsString(it), ContentType.APPLICATION_JSON)
        }
        return execute(request, headers)
    }

    fun put(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()): RestResponse {
        val request = HttpPut(buildUrl(path))
        body?.let {
            request.entity = StringEntity(objectMapper.writeValueAsString(it), ContentType.APPLICATION_JSON)
        }
        return execute(request, headers)
    }

    fun patch(path: String, body: Any? = null, headers: Map<String, String> = emptyMap()): RestResponse {
        val request = HttpPatch(buildUrl(path))
        body?.let {
            request.entity = StringEntity(objectMapper.writeValueAsString(it), ContentType.APPLICATION_JSON)
        }
        return execute(request, headers)
    }

    fun delete(path: String, headers: Map<String, String> = emptyMap()): RestResponse {
        return execute(HttpDelete(buildUrl(path)), headers)
    }

    private fun execute(request: HttpUriRequestBase, headers: Map<String, String>): RestResponse {
        headers.forEach { (name, value) -> request.addHeader(name, value) }
        
        return try {
            httpClient.execute(request) { response ->
                val statusCode = response.code
                val body = response.entity?.let { String(it.content.readAllBytes()) }
                RestResponse(statusCode, body, objectMapper)
            }
        } catch (e: Exception) {
            log.error("Error executing request ${request.method} ${request.path}: ${e.message}", e)
            RestResponse(500, "Error: ${e.message}", objectMapper)
        }
    }

    private fun buildUrl(path: String): String {
        val sanitizedBaseUrl = baseUrl.removeSuffix("/")
        val sanitizedPath = if (path.startsWith("/")) path else "/$path"
        return "$sanitizedBaseUrl$sanitizedPath"
    }

    fun createAuthHeader(token: String): Map<String, String> {
        return mapOf("Authorization" to "Bearer $token")
    }

    /**
     * Convenience function for tests where we can't easily access JwtTokenService.
     * Use with caution.
     */
    fun createAuthHeader(
        subject: String,
        username: String,
        roles: List<String>,
        issuer: String = "test-issuer",
        key: String = "test-key-must-be-at-least-32-chars-long-!!!-123456"
    ): Map<String, String> {
        val token = com.auth0.jwt.JWT.create()
            .withIssuer(issuer)
            .withSubject(subject)
            .withClaim("username", username)
            .withArrayClaim("roles", roles.toTypedArray())
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 3600000))
            .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(key))
        return createAuthHeader(token)
    }

    override fun close() {
        httpClient.close()
        connectionManager.close()
    }

    fun closeConnections() {
        connectionManager.closeIdle(TimeValue.NEG_ONE_MILLISECOND)
        connectionManager.closeExpired()
    }
}
