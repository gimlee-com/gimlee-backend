package com.gimlee.api.web

import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.domain.model.UserPresenceStatus
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class UserPresenceControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val userPresenceService: UserPresenceService
) : BaseIntegrationTest({

    Given("a user with presence") {
        val userId = ObjectId.get()
        val username = "presence_test_user"
        userRepository.save(User(id = userId, username = username))
        
        userPresenceService.trackActivity(userId.toHexString())
        userPresenceService.updateStatus(userId.toHexString(), UserPresenceStatus.ONLINE, "Available")
        userPresenceService.flushBuffer()

        When("fetching presence by userName") {
            val result = mockMvc.get("/user/$username/presence") {
                requestAttr("principal", com.gimlee.auth.model.Principal(userId.toHexString(), username, listOf(com.gimlee.auth.model.Role.USER)))
            }.andReturn()

            Then("it should return 200 OK and correct status") {
                result.response.status shouldBe 200
                result.response.contentAsString.contains("\"status\":\"ONLINE\"") shouldBe true
                result.response.contentAsString.contains("\"customStatus\":\"Available\"") shouldBe true
            }
        }
    }
})
