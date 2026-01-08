package com.gimlee.api

import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.domain.ProfileService
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class SessionInitIntegrationTest(
    private val mockMvc: MockMvc,
    private val profileService: ProfileService
) : BaseIntegrationTest({

    Given("an authenticated user with a profile") {
        val userId = ObjectId.get().toHexString()
        val principal = Principal(userId = userId, username = "testuser", roles = listOf(Role.USER))
        val avatarUrl = "https://example.com/avatar.png"
        
        profileService.updateAvatar(userId, avatarUrl)

        When("calling session init with userProfile decorator") {
            val result = mockMvc.get("/session/init") {
                param("decorators", "userProfile")
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            Then("it should return user profile data") {
                val content = result.response.contentAsString
                content.contains("\"userProfile\":") shouldBe true
                content.contains("\"avatarUrl\":\"$avatarUrl\"") shouldBe true
                content.contains("\"userId\":\"$userId\"") shouldBe true
            }
        }

        When("calling session init with accessToken decorator") {
            val result = mockMvc.get("/session/init") {
                param("decorators", "accessToken")
                header("Authorization", "Bearer mocked-token")
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            Then("it should return the access token") {
                val content = result.response.contentAsString
                content.contains("\"accessToken\":\"mocked-token\"") shouldBe true
            }
        }

        When("calling session init with both decorators") {
            val result = mockMvc.get("/session/init") {
                param("decorators", "accessToken,userProfile")
                header("Authorization", "Bearer mocked-token")
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            Then("it should return both access token and user profile") {
                val content = result.response.contentAsString
                content.contains("\"accessToken\":\"mocked-token\"") shouldBe true
                content.contains("\"userProfile\":") shouldBe true
                content.contains("\"userId\":\"$userId\"") shouldBe true
            }
        }
    }
    
    Given("an unauthenticated user") {
        When("calling session init with both decorators") {
             val result = mockMvc.get("/session/init") {
                param("decorators", "accessToken,userProfile")
            }.andExpect {
                status { isOk() }
            }.andReturn()

            Then("it should return null or empty values for decorators") {
                val content = result.response.contentAsString
                content.contains("\"accessToken\":\"\"") shouldBe true
                content.contains("\"userProfile\":null") shouldBe true
            }
        }
    }
})
