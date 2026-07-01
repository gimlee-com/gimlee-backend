package com.gimlee.chat

import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.chat.persistence.model.ConversationDocument
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId
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
 * Verifies that real-time chat SSE delivery works end-to-end when configured with the Redis
 * transport (gimlee.chat.sse.transport=redis). A Redis Testcontainer backs the Pub/Sub channel.
 *
 * Even on a single instance this exercises the full Redis round-trip: an event published by
 * [ChatService] is sent to Redis and delivered back to the same instance's broadcaster via the
 * Redis subscription, then pushed to the SSE subscriber. See ADR 0005.
 */
class ChatRedisTransportIntegrationTest(
    private val conversationService: ConversationService,
    @Suppress("unused") private val chatService: ChatService
) : BaseIntegrationTest({

    val userAId = ObjectId.get().toHexString()
    val userBId = ObjectId.get().toHexString()

    fun userHeaders(userId: String, username: String): Map<String, String> =
        restClient.createAuthHeader(subject = userId, username = username, roles = listOf("USER"))

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

    Given("an SSE subscriber on a Redis-backed chat instance") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        val token = restClient.createAuthHeader(
            subject = userAId,
            username = "userA",
            roles = listOf("USER")
        )["Authorization"]!!.removePrefix("Bearer ")

        val eventQueue = LinkedBlockingQueue<String>()
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/chat/$conversationId/events"))
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

        When("user B sends a message") {
            restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "Redis SSE test message"),
                userHeaders(userBId, "userB")
            )

            Then("user A receives the event via SSE through the Redis transport") {
                val eventData = eventQueue.poll(5, TimeUnit.SECONDS)
                eventData shouldNotBe null
                eventData!! shouldContain "Redis SSE test message"
                eventData shouldContain "MESSAGE"
            }
        }

        When("user B indicates typing") {
            restClient.post(
                "/chat/$conversationId/typing",
                null,
                userHeaders(userBId, "userB")
            )

            Then("user A receives the typing indicator through the Redis transport") {
                val eventData = eventQueue.poll(5, TimeUnit.SECONDS)
                eventData shouldNotBe null
                eventData!! shouldContain "TYPING_INDICATOR"
            }
        }

        subscriberThread.interrupt()
    }

    Given("a Redis-backed subscriber expecting multiple ordered messages") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        val token = restClient.createAuthHeader(
            subject = userAId,
            username = "userA",
            roles = listOf("USER")
        )["Authorization"]!!.removePrefix("Bearer ")

        val eventQueue = LinkedBlockingQueue<String>()
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/chat/$conversationId/events"))
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

        When("user B sends two messages in sequence") {
            restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "ordering-first-AAA"),
                userHeaders(userBId, "userB")
            )
            restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "ordering-second-BBB"),
                userHeaders(userBId, "userB")
            )

            Then("user A receives them in the order they were sent") {
                val combined = StringBuilder()
                val deadline = System.currentTimeMillis() + 5000
                while (System.currentTimeMillis() < deadline &&
                    !(combined.contains("ordering-first-AAA") && combined.contains("ordering-second-BBB"))
                ) {
                    val line = eventQueue.poll(500, TimeUnit.MILLISECONDS)
                    if (line != null) combined.append(line).append('\n')
                }

                val received = combined.toString()
                received shouldContain "ordering-first-AAA"
                received shouldContain "ordering-second-BBB"
                received.indexOf("ordering-first-AAA") shouldBeLessThan received.indexOf("ordering-second-BBB")
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
