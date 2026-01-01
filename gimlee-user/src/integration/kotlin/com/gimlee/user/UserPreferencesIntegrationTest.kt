package com.gimlee.user
    
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.web.dto.request.UpdateUserPreferencesRequestDto
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class UserPreferencesIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val userPreferencesService: UserPreferencesService
) : BaseIntegrationTest({

    Given("a user") {
        val userId = ObjectId.get().toHexString()
        val principal = Principal(userId = userId, username = "testuser", roles = listOf(Role.USER))

        // We need to mock the authentication to bypass the security for integration tests that don't go through the login flow
        // Or we could use the actual auth token, but mocking is easier for this specific test
        
        beforeTest {
            mockkStatic(RequestContextHolder::class)
            val requestAttributes = io.mockk.mockk<RequestAttributes>(relaxed = true)
            every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
            every { requestAttributes.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal
        }

        afterTest {
            unmockkAll()
        }

        When("getting default user preferences") {
            val response = userPreferencesService.getUserPreferences(userId)
            
            Then("the default language should be en-US") {
                response.language shouldBe "en-US"
            }
        }

        When("updating user preferences with valid IETF tag") {
            val updated = userPreferencesService.updateUserPreferences(userId, "pl-PL")
            
            Then("the preferences should be updated") {
                updated.language shouldBe "pl-PL"
                userPreferencesService.getUserPreferences(userId).language shouldBe "pl-PL"
            }
        }
    }
})
