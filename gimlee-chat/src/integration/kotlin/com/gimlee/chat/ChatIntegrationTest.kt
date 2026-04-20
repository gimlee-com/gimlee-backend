package com.gimlee.chat

import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.chat.persistence.model.ConversationDocument
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ChatIntegrationTest(
    private val conversationService: ConversationService,
    private val chatService: ChatService,
    private val chatRepository: ChatRepository
) : BaseIntegrationTest({

    val userAId = ObjectId.get().toHexString()
    val userBId = ObjectId.get().toHexString()
    val userCId = ObjectId.get().toHexString()

    fun userHeaders(userId: String, username: String = "user_${userId.take(6)}"): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId,
            username = username,
            roles = listOf("USER")
        )

    fun createTestConversation(
        participantIds: List<String>,
        type: String = "TEST",
        linkType: String? = null,
        linkId: String? = null
    ): String {
        val (conversation, outcome) = conversationService.createConversation(
            type = type,
            participantUserIds = participantIds,
            participantRole = ParticipantRole.MEMBER,
            linkType = linkType,
            linkId = linkId
        )
        outcome shouldBe null
        return conversation.id
    }

    beforeSpec {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
    }

    // =========================================================
    // CONVERSATION + MESSAGE FLOW
    // =========================================================

    Given("a conversation between user A and user B") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        When("user A sends a message") {
            val response = restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "Hello from A!"),
                userHeaders(userAId)
            )

            Then("the message should be sent successfully") {
                response.statusCode shouldBe 200
            }

            And("the message should appear in history") {
                val historyResponse = restClient.get(
                    "/chat/$conversationId/messages?limit=20",
                    userHeaders(userAId)
                )
                historyResponse.statusCode shouldBe 200
                val body = historyResponse.bodyAs<Map<String, Any>>()!!
                val messages = body["messages"] as List<*>
                messages shouldHaveSize 1
                @Suppress("UNCHECKED_CAST")
                val msg = messages[0] as Map<String, Any>
                msg["text"] shouldBe "Hello from A!"
                msg["authorId"] shouldBe userAId
            }
        }

        When("user B also sends a message") {
            restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "Reply from B!"),
                userHeaders(userBId)
            )

            Then("history should contain both messages (newest first)") {
                val historyResponse = restClient.get(
                    "/chat/$conversationId/messages?limit=20",
                    userHeaders(userBId)
                )
                val body = historyResponse.bodyAs<Map<String, Any>>()!!
                val messages = body["messages"] as List<*>
                messages.size shouldBe 2
                @Suppress("UNCHECKED_CAST")
                (messages[0] as Map<String, Any>)["text"] shouldBe "Reply from B!"
                @Suppress("UNCHECKED_CAST")
                (messages[1] as Map<String, Any>)["text"] shouldBe "Hello from A!"
            }
        }
    }

    // =========================================================
    // ACCESS CONTROL: NON-PARTICIPANT DENIED
    // =========================================================

    Given("a conversation that user C is not part of") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        When("user C tries to send a message") {
            val response = restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "Intruder!"),
                userHeaders(userCId)
            )

            Then("it should be denied with 403") {
                response.statusCode shouldBe 403
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "CHAT_NOT_A_PARTICIPANT"
            }
        }

        When("user C tries to read history") {
            val response = restClient.get(
                "/chat/$conversationId/messages?limit=20",
                userHeaders(userCId)
            )

            Then("it should be denied with 403") {
                response.statusCode shouldBe 403
            }
        }

        When("user C tries to indicate typing") {
            val response = restClient.post(
                "/chat/$conversationId/typing",
                null,
                userHeaders(userCId)
            )

            Then("it should be denied with 403") {
                response.statusCode shouldBe 403
            }
        }

        When("user C tries to get conversation details") {
            val response = restClient.get(
                "/conversations/$conversationId",
                userHeaders(userCId)
            )

            Then("it should be denied with 403") {
                response.statusCode shouldBe 403
            }
        }
    }

    // =========================================================
    // LOCKED CONVERSATION: WRITE DENIED, READ ALLOWED
    // =========================================================

    Given("a locked conversation") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        // Send a message before locking
        chatService.sendMessage(conversationId, userAId, "userA", "Before lock")
        conversationService.lockConversation(conversationId)

        When("user A tries to send a message") {
            val response = restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "After lock"),
                userHeaders(userAId)
            )

            Then("it should be denied with 403 CONVERSATION_LOCKED") {
                response.statusCode shouldBe 403
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "CHAT_CONVERSATION_LOCKED"
            }
        }

        When("user A reads history") {
            val response = restClient.get(
                "/chat/$conversationId/messages?limit=20",
                userHeaders(userAId)
            )

            Then("it should succeed (reads allowed on locked)") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val messages = body["messages"] as List<*>
                messages shouldHaveSize 1
            }
        }

        When("user A gets conversation details") {
            val response = restClient.get(
                "/conversations/$conversationId",
                userHeaders(userAId)
            )

            Then("the status should be LOCKED") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as Map<String, Any>
                data["status"] shouldBe "LOCKED"
            }
        }
    }

    // =========================================================
    // ARCHIVED CONVERSATION: BOTH DENIED
    // =========================================================

    Given("an archived conversation") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))
        conversationService.archiveConversation(conversationId)

        When("user A tries to send a message") {
            val response = restClient.post(
                "/chat/$conversationId/messages",
                mapOf("message" to "After archive"),
                userHeaders(userAId)
            )

            Then("it should be denied") {
                response.statusCode shouldBe 403
            }
        }

        When("user A tries to read history") {
            val response = restClient.get(
                "/chat/$conversationId/messages?limit=20",
                userHeaders(userAId)
            )

            Then("it should be denied") {
                response.statusCode shouldBe 403
            }
        }
    }

    // =========================================================
    // NON-EXISTENT CONVERSATION → 404
    // =========================================================

    Given("a non-existent conversation ID") {
        val fakeId = ObjectId.get().toHexString()

        When("user A tries to send a message") {
            val response = restClient.post(
                "/chat/$fakeId/messages",
                mapOf("message" to "Hello?"),
                userHeaders(userAId)
            )

            Then("it should return 404") {
                response.statusCode shouldBe 404
            }
        }

        When("user A tries to get history") {
            val response = restClient.get(
                "/chat/$fakeId/messages?limit=20",
                userHeaders(userAId)
            )

            Then("it should return 404") {
                response.statusCode shouldBe 404
            }
        }

        When("user A tries to get conversation details") {
            val response = restClient.get(
                "/conversations/$fakeId",
                userHeaders(userAId)
            )

            Then("it should return 404") {
                response.statusCode shouldBe 404
            }
        }
    }

    // =========================================================
    // UNAUTHENTICATED REQUEST → 401
    // =========================================================

    Given("no auth token") {
        val fakeId = ObjectId.get().toHexString()

        When("requesting messages without auth") {
            val response = restClient.get("/chat/$fakeId/messages?limit=20")

            Then("it should return 401") {
                response.statusCode shouldBe 401
            }
        }

        When("requesting conversations without auth") {
            val response = restClient.get("/conversations")

            Then("it should return 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // =========================================================
    // CONVERSATION LIST
    // =========================================================

    Given("multiple conversations for user A") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)

        val convId1 = createTestConversation(listOf(userAId, userBId), type = "FIRST")
        Thread.sleep(10) // Ensure different timestamps
        val convId2 = createTestConversation(listOf(userAId, userCId), type = "SECOND")

        When("user A lists conversations") {
            val response = restClient.get(
                "/conversations?limit=20",
                userHeaders(userAId)
            )

            Then("both conversations should be returned, newest first") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val conversations = data["conversations"] as List<Map<String, Any>>
                conversations shouldHaveSize 2
                conversations[0]["id"] shouldBe convId2
                conversations[1]["id"] shouldBe convId1
            }
        }

        When("a message is sent to the older conversation") {
            chatService.sendMessage(convId1, userAId, "userA", "Bump activity")
            conversationService.updateLastActivity(convId1)

            Then("that conversation should move to the top") {
                val response = restClient.get(
                    "/conversations?limit=20",
                    userHeaders(userAId)
                )
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val conversations = data["conversations"] as List<Map<String, Any>>
                conversations[0]["id"] shouldBe convId1
            }
        }

        When("user B lists conversations") {
            val response = restClient.get(
                "/conversations?limit=20",
                userHeaders(userBId)
            )

            Then("only conversations involving user B should appear") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val conversations = data["conversations"] as List<Map<String, Any>>
                conversations shouldHaveSize 1
                conversations[0]["id"] shouldBe convId1
            }
        }
    }

    // =========================================================
    // GET SINGLE CONVERSATION
    // =========================================================

    Given("a conversation to retrieve") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId), type = "DETAIL_TEST")

        When("user A gets conversation details") {
            val response = restClient.get(
                "/conversations/$conversationId",
                userHeaders(userAId)
            )

            Then("it should return full conversation details") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as Map<String, Any>
                data["id"] shouldBe conversationId
                data["type"] shouldBe "DETAIL_TEST"
                data["status"] shouldBe "ACTIVE"
                @Suppress("UNCHECKED_CAST")
                val participants = data["participants"] as List<Map<String, Any>>
                participants shouldHaveSize 2
            }
        }
    }

    // =========================================================
    // HISTORY PAGINATION
    // =========================================================

    Given("a conversation with multiple messages for pagination") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        // Send 5 messages
        repeat(5) { i ->
            chatService.sendMessage(conversationId, userAId, "userA", "Message ${i + 1}")
        }

        When("requesting with limit=2") {
            val response = restClient.get(
                "/chat/$conversationId/messages?limit=2",
                userHeaders(userAId)
            )

            Then("it should return 2 messages and hasMore=true") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val messages = body["messages"] as List<*>
                messages shouldHaveSize 2
                body["hasMore"] shouldBe true
                @Suppress("UNCHECKED_CAST")
                (messages[0] as Map<String, Any>)["text"] shouldBe "Message 5"
                @Suppress("UNCHECKED_CAST")
                (messages[1] as Map<String, Any>)["text"] shouldBe "Message 4"
            }
        }

        When("requesting with cursor pagination") {
            // Get first page
            val firstPage = restClient.get(
                "/chat/$conversationId/messages?limit=2",
                userHeaders(userAId)
            )
            val firstBody = firstPage.bodyAs<Map<String, Any>>()!!
            @Suppress("UNCHECKED_CAST")
            val firstMessages = firstBody["messages"] as List<Map<String, Any>>
            val lastId = firstMessages.last()["id"] as String

            // Get second page
            val secondPage = restClient.get(
                "/chat/$conversationId/messages?limit=2&beforeId=$lastId",
                userHeaders(userAId)
            )

            Then("the second page should contain the next messages") {
                secondPage.statusCode shouldBe 200
                val body = secondPage.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val messages = body["messages"] as List<Map<String, Any>>
                messages shouldHaveSize 2
                messages[0]["text"] shouldBe "Message 3"
                messages[1]["text"] shouldBe "Message 2"
            }
        }
    }

    // =========================================================
    // SYSTEM MESSAGES
    // =========================================================

    Given("a conversation for system messages") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)
        mongoTemplate.remove(Query(), ChatRepository.COLLECTION_NAME)
        val conversationId = createTestConversation(listOf(userAId, userBId))

        When("a system message is sent") {
            chatService.sendSystemMessage(conversationId, "STATUS_CHANGED", mapOf("status" to "COMPLETE"))

            Then("it should appear in history with proper structure") {
                val response = restClient.get(
                    "/chat/$conversationId/messages?limit=20",
                    userHeaders(userAId)
                )
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val messages = body["messages"] as List<*>
                messages shouldHaveSize 1
                @Suppress("UNCHECKED_CAST")
                val msg = messages[0] as Map<String, Any>
                msg["text"] shouldBe null
                msg["messageType"] shouldBe "SYSTEM"
                msg["systemCode"] shouldBe "STATUS_CHANGED"
                @Suppress("UNCHECKED_CAST")
                val args = msg["systemArgs"] as Map<String, String>
                args["status"] shouldBe "COMPLETE"
            }
        }
    }

    // =========================================================
    // IDEMPOTENT LINKED CONVERSATION
    // =========================================================

    Given("a linked conversation creation") {
        mongoTemplate.remove(Query(), ConversationDocument.COLLECTION_NAME)

        When("creating the same linked conversation twice") {
            val (conv1, outcome1) = conversationService.createConversation(
                type = "ORDER",
                participantUserIds = listOf(userAId, userBId),
                linkType = "PRC",
                linkId = "purchase-123"
            )
            val (conv2, outcome2) = conversationService.createConversation(
                type = "ORDER",
                participantUserIds = listOf(userAId, userBId),
                linkType = "PRC",
                linkId = "purchase-123"
            )

            Then("both should return the same conversation") {
                outcome1 shouldBe null
                outcome2 shouldBe null
                conv1.id shouldBe conv2.id
            }
        }

        When("creating conversations with different link IDs") {
            val (conv1, _) = conversationService.createConversation(
                type = "ORDER",
                participantUserIds = listOf(userAId, userBId),
                linkType = "PRC",
                linkId = "purchase-AAA"
            )
            val (conv2, _) = conversationService.createConversation(
                type = "ORDER",
                participantUserIds = listOf(userAId, userBId),
                linkType = "PRC",
                linkId = "purchase-BBB"
            )

            Then("they should be different conversations") {
                conv1.id shouldNotBe conv2.id
            }
        }
    }

    // =========================================================
    // SSE EVENTS
    // =========================================================

    Given("an SSE subscriber") {
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
                mapOf("message" to "SSE test message"),
                userHeaders(userBId, "userB")
            )

            Then("user A should receive the event via SSE") {
                val eventData = eventQueue.poll(5, TimeUnit.SECONDS)
                eventData shouldNotBe null
                eventData!! shouldContain "SSE test message"
                eventData shouldContain "MESSAGE"
            }
        }

        When("user B indicates typing") {
            restClient.post(
                "/chat/$conversationId/typing",
                null,
                userHeaders(userBId, "userB")
            )

            Then("user A should receive the typing indicator") {
                val eventData = eventQueue.poll(5, TimeUnit.SECONDS)
                eventData shouldNotBe null
                eventData!! shouldContain "TYPING_INDICATOR"
            }
        }

        subscriberThread.interrupt()
    }
})
