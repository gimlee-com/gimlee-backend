package com.gimlee.chat

import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.chat.persistence.model.ConversationDocument
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Verifies cross-instance real-time fan-out: two independent application contexts share a single
 * Redis. A message published by instance 1 must reach an SSE subscriber connected to instance 2,
 * proving the Redis transport removes the need for load-balancer chat affinity. See ADR 0005.
 *
 * Instance 1 is the standard [BaseIntegrationTest] context (configured with the Redis transport).
 * Instance 2 is booted manually with [SpringApplicationBuilder], pointed at the same Redis and
 * MongoDB, and shut down in afterSpec.
 */
class ChatRedisCrossInstanceIntegrationTest(
    private val conversationService: ConversationService
) : BaseIntegrationTest({

    val userAId = ObjectId.get().toHexString()
    val userBId = ObjectId.get().toHexString()

    fun createTestConversation(participantIds: List<String>): String {
        val (conversation, outcome) = conversationService.createConversation(
            type = "TEST",
            participantUserIds = participantIds,
            participantRole = ParticipantRole.MEMBER,
            linkType = null,
            linkId = null
        )
        outcome shouldBe null
        return conversation.id
    }

    var instance2: ConfigurableApplicationContext? = null

    afterSpec {
        instance2?.close()
    }

    Given("a second app instance sharing Redis with the first") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        // Highest-precedence overrides for instance 2. The Gradle integration task activates the
        // "integration" profile (via a JVM system property) which instance 2 inherits, so these
        // must be added with addFirst to win over application-integration.yaml.
        val overrides = mapOf<String, Any>(
            "server.port" to "0",
            "spring.data.mongodb.uri" to environment.getProperty("spring.data.mongodb.uri")!!,
            "spring.data.redis.host" to environment.getProperty("spring.data.redis.host")!!,
            "spring.data.redis.port" to environment.getProperty("spring.data.redis.port")!!,
            "gimlee.chat.sse.transport" to "redis",
            // Must match the key/issuer RestTestClient.createAuthHeader signs tokens with,
            // so tokens minted for instance 1 validate on instance 2.
            "gimlee.auth.rest.jwt.key" to "test-key-must-be-at-least-32-chars-long-!!!-123456",
            "gimlee.auth.rest.jwt.issuer" to "test-issuer"
        )
        instance2 = SpringApplicationBuilder(ChatTestApplication::class.java)
            .initializers(ApplicationContextInitializer<ConfigurableApplicationContext> { ctx ->
                ctx.environment.propertySources.addFirst(MapPropertySource("crossInstanceOverrides", overrides))
            })
            .run()
        val port2 = instance2!!.environment.getProperty("local.server.port")!!.toInt()

        // User A subscribes on instance 2.
        val token = restClient.createAuthHeader(
            subject = userAId,
            username = "userA",
            roles = listOf("USER")
        )["Authorization"]!!.removePrefix("Bearer ")

        val eventQueue = LinkedBlockingQueue<String>()
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port2/chat/$conversationId/events"))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val subscriberThread = thread(start = true) {
            try {
                client.send(request, HttpResponse.BodyHandlers.ofLines()).body().use { stream ->
                    stream.forEach { line ->
                        if (line.startsWith("data:")) {
                            eventQueue.offer(line.substring(5).trim())
                        }
                    }
                }
            } catch (_: Exception) {
                // Shutdown
            }
        }

        Thread.sleep(1000)

        When("user B sends a message via instance 1") {
            restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "cross-instance message"),
                restClient.createAuthHeader(subject = userBId, username = "userB", roles = listOf("USER"))
            )

            Then("user A receives it on instance 2 through Redis") {
                val eventData = eventQueue.poll(5, TimeUnit.SECONDS)
                eventData shouldNotBe null
                eventData!! shouldContain "cross-instance message"
                eventData shouldContain "MESSAGE"
            }
        }

        subscriberThread.interrupt()
    }
}) {
    companion object {
        private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("gimlee.chat.sse.transport") { "redis" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
