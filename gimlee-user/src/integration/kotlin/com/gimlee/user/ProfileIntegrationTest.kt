package com.gimlee.user

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.web.dto.request.UpdateAvatarRequestDto
import com.gimlee.user.web.dto.response.UserProfileDto
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class ProfileIntegrationTest(
    private val restTemplate: TestRestTemplate
) : BaseIntegrationTest({

    Given("an authenticated user") {
        val userId = ObjectId.get().toHexString()
        val principal = Principal(userId = userId, username = "testuser", roles = listOf(Role.USER))

        beforeTest {
            mockkStatic(RequestContextHolder::class)
            val requestAttributes = io.mockk.mockk<RequestAttributes>(relaxed = true)
            every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
            every { requestAttributes.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal
        }

        afterTest {
            unmockkAll()
        }

        When("updating the user avatar") {
            val avatarUrl = "https://example.com/new-avatar.png"
            val request = UpdateAvatarRequestDto(avatarUrl = avatarUrl)
            val response = restTemplate.exchange(
                "/user/profile/avatar",
                HttpMethod.PUT,
                HttpEntity(request),
                UserProfileDto::class.java
            )

            Then("the response status should be 200 OK") {
                response.statusCode shouldBe HttpStatus.OK
            }

            Then("the response should contain the updated avatar URL") {
                val body = response.body
                body shouldNotBe null
                body?.userId shouldBe userId
                body?.avatarUrl shouldBe avatarUrl
                body?.updatedAt shouldNotBe 0L
            }
        }
    }
})
