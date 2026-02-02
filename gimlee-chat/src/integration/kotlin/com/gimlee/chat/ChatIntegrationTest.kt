package com.gimlee.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.persistence.ChatRepository
import com.gimlee.chat.web.dto.request.NewMessageRequestDto
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = [
    "gimlee.chat.sse.buffer-ms=100"
])
class ChatIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val chatRepository: ChatRepository,
    private val chatService: ChatService,
    private val jwtTokenService: JwtTokenService,
    @org.springframework.beans.factory.annotation.Value("\${local.server.port}") private val port: Int
) : BaseIntegrationTest({

    Given("a chat session") {
        val chatId = "test-chat-1"
        val userId = org.bson.types.ObjectId.get().toHexString()
        val username = "testuser"
        val principal = Principal(userId = userId, username = username, roles = listOf(Role.USER))
        val token = jwtTokenService.generateToken(userId, username, listOf(Role.USER), false)

        chatRepository.clear()

        When("subscribing to real-time events") {
            val eventQueue = java.util.concurrent.LinkedBlockingQueue<String>()
            val client = java.net.http.HttpClient.newHttpClient()
            val request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:$port/chat/$chatId/events"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()

            val subscriberThread = kotlin.concurrent.thread(start = true) {
                try {
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines()).body().use { stream ->
                        stream.forEach { line ->
                            if (line.startsWith("data:")) {
                                eventQueue.offer(line.substring(5).trim())
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors during shutdown
                }
            }

            // Wait a bit for connection to be established
            Thread.sleep(1000)

            And("sending a message via REST") {
                val messageText = "SSE test message"
                val messageRequest = NewMessageRequestDto(messageText)
                mockMvc.post("/chat/$chatId/messages") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(messageRequest)
                }.andExpect {
                    status { isOk() }
                }

                Then("the event should be received by the SSE subscriber") {
                    val eventData = eventQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                    eventData shouldNotBe null
                    eventData!! shouldContain messageText
                    eventData!! shouldContain "MESSAGE"
                }
            }

            And("indicating typing") {
                mockMvc.post("/chat/$chatId/typing") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                }

                Then("the typing indicator should be received by the SSE subscriber") {
                    val eventData = eventQueue.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                    eventData shouldNotBe null
                    eventData!! shouldContain "TYPING_INDICATOR"
                }
            }

            subscriberThread.interrupt()
        }

        When("sending a message via REST (history verification)") {
            val historyChatId = "test-history"
            val request = NewMessageRequestDto("Hello world!")
            mockMvc.post("/chat/$historyChatId/messages") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
            }

            Then("the message should be immediately in DB") {
                val history = chatService.getHistory(historyChatId, 10, null)
                history shouldHaveSize 1
                history[0].text shouldBe "Hello world!"
                
                val dbMessages = chatRepository.findMessages(historyChatId, 10, null)
                dbMessages shouldHaveSize 1
            }
        }

        When("requesting history with multiple messages") {
            val historyChatId = "test-history-multi"
            chatService.sendMessage(historyChatId, "otheruser", "Hi there!")
            chatService.sendMessage(historyChatId, "otheruser", "How are you?")
            
            Then("it should return all messages") {
                val history = chatService.getHistory(historyChatId, 10, null)
                history shouldHaveSize 2
                history[0].text shouldBe "How are you?"
                history[1].text shouldBe "Hi there!"
            }
        }
    }
})
