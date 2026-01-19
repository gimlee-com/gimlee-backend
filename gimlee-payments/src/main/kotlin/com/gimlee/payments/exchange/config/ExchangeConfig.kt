package com.gimlee.payments.exchange.config

import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExchangeConfig {

    companion object {
        const val EXCHANGE_HTTP_CLIENT = "exchangeHttpClient"
    }

    @Bean(EXCHANGE_HTTP_CLIENT)
    fun exchangeHttpClient(): HttpClient {
        return HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(1)
                    .setMaxConnPerRoute(1)
                    .build()
            )
            .build()
    }
}
