package com.gimlee.api

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
abstract class BaseIntegrationTest(body: BehaviorSpec.() -> Unit) : BehaviorSpec({
    extensions(SpringExtension)
    body()
}) {
    companion object {
        private val mongodb = MongoDBContainer("mongo:8.0").apply {
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
        }
    }
}
