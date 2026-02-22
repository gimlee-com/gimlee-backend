package com.gimlee.common

import com.gimlee.common.config.MongoClientConfig
import com.gimlee.common.http.RestTestClient
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(MongoClientConfig::class)
abstract class BaseIntegrationTest(body: BaseIntegrationTest.() -> Unit) : BehaviorSpec() {

    @Autowired
    lateinit var environment: Environment

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    init {
        extensions(SpringExtension)

        beforeSpec {
            if (!wireMockServer.isRunning) {
                wireMockServer.start()
            }

            // Seed exchange rates for AdPriceValidator
            try {
                if (mongoTemplate.collectionExists("gimlee-payments-exchange-rates")) {
                    mongoTemplate.dropCollection("gimlee-payments-exchange-rates")
                }
                
                val nowMicros = System.currentTimeMillis() * 1000
                val rates = listOf(
                    // ARRR -> USD
                    Document("_id", ObjectId.get())
                        .append("bc", "ARRR").append("qc", "USD")
                        .append("r", Decimal128(BigDecimal("0.5")))
                        .append("src", "MOCK").append("ua", nowMicros).append("iv", false),
                    // YEC -> USD
                    Document("_id", ObjectId.get())
                        .append("bc", "YEC").append("qc", "USD")
                        .append("r", Decimal128(BigDecimal("0.2")))
                        .append("src", "MOCK").append("ua", nowMicros).append("iv", false),
                    // USD -> ARRR
                    Document("_id", ObjectId.get())
                        .append("bc", "USD").append("qc", "ARRR")
                        .append("r", Decimal128(BigDecimal("2.0")))
                        .append("src", "MOCK").append("ua", nowMicros).append("iv", false),
                    // USD -> PLN
                    Document("_id", ObjectId.get())
                        .append("bc", "USD").append("qc", "PLN")
                        .append("r", Decimal128(BigDecimal("4.0")))
                        .append("src", "MOCK").append("ua", nowMicros).append("iv", false)
                )
                mongoTemplate.insert(rates, "gimlee-payments-exchange-rates")
            } catch (e: Exception) {
                // Ignore - exchange rates may not be needed for all tests
            }
        }

        body()
    }

    @LocalServerPort
    var port: Int = 0

    val restClient: RestTestClient by lazy {
        val contextPath = environment.getProperty("server.servlet.context-path", "")
        RestTestClient("http://localhost:$port$contextPath")
    }


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
            registry.add("gimlee.auth.rest.jwt.key") { "test-key-must-be-at-least-32-chars-long-!!!-123456" }
            registry.add("gimlee.auth.rest.jwt.issuer") { "test-issuer" }
        }
    }
}
