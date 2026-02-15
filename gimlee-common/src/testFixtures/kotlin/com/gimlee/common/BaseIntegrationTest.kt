package com.gimlee.common

import com.gimlee.common.config.MongoClientConfig
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(MongoClientConfig::class)
abstract class BaseIntegrationTest(body: BehaviorSpec.() -> Unit) : BehaviorSpec({
    extensions(SpringExtension)

    beforeSpec {
        if (!wireMockServer.isRunning) {
            wireMockServer.start()
        }
    }

    body()
}) {
    companion object {
        private val mongodb = MongoDBContainer("mongo:8.0").apply {
            start()
        }

        val wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()).apply {
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
            registry.add("wiremock.server.port") { wireMockServer.port() }
            registry.add("gimlee.payments.exchange.cache.expire-after-write-seconds") { "0" }
        }
    }
}
