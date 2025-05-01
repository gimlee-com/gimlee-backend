package com.gimlee.payments.piratechain.config

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
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.config.PirateChainClientProperties
import java.net.URI

@Configuration
@EnableConfigurationProperties(PirateChainClientProperties::class)
class PirateChainClientConfig(
    private val properties: PirateChainClientProperties
) {

    companion object {
        const val PIRATE_CHAIN_HTTP_CLIENT = "pirateChainHttpClient"
        const val DEFAULT_KEEP_ALIVE_MINUTES = 3L
    }

    @Bean
    @Qualifier(PIRATE_CHAIN_HTTP_CLIENT)
    fun httpClient(): HttpClient = with(properties) {
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
    fun pirateChainRpcClient(
        @Qualifier(PIRATE_CHAIN_HTTP_CLIENT) httpClient: HttpClient
    ) = PirateChainRpcClient(httpClient, properties)
}