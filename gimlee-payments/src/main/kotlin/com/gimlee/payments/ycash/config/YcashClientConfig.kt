package com.gimlee.payments.ycash.config

import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.gimlee.payments.ycash.client.YcashRpcClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(YcashClientProperties::class)
class YcashClientConfig(
    private val properties: YcashClientProperties,
) {

    companion object {
        const val YCASH_HTTP_CLIENT = "ycashHttpClient"
        const val DEFAULT_KEEP_ALIVE_MINUTES = 3L
    }

    @Bean
    @Qualifier(YCASH_HTTP_CLIENT)
    fun ycashHttpClient(): HttpClient = with(properties) {
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(maxConnections)
                    .build()
            )
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMillis))
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeoutMillis))
                    .setConnectionKeepAlive(TimeValue.ofMinutes(DEFAULT_KEEP_ALIVE_MINUTES))
                    .build()
            )
            .setDefaultCredentialsProvider(
                BasicCredentialsProvider().apply {
                    val uri = URI(rpcUrl)
                    setCredentials(
                        AuthScope(uri.host, uri.port),
                        UsernamePasswordCredentials(user, password.toCharArray())
                    )
                }
            )
            .disableAutomaticRetries()
            .build()
    }

    @Bean
    fun ycashRpcClient(
        @Qualifier(YCASH_HTTP_CLIENT) httpClient: HttpClient
    ) = YcashRpcClient(httpClient, properties)
}
