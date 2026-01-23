package com.gimlee.user
    
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.config.UserPreferencesProperties
import com.gimlee.user.domain.UserPreferencesService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.bson.types.ObjectId
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import java.util.*

class UserPreferencesIntegrationTest(
    private val restTemplate: TestRestTemplate,
    private val userPreferencesService: UserPreferencesService,
    private val properties: UserPreferencesProperties
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
            LocaleContextHolder.resetLocaleContext()
        }

        When("getting default user preferences") {
            val response = userPreferencesService.getUserPreferences(userId)
            
            Then("the default language should be en-US and default currency should be as configured in properties") {
                response.language shouldBe "en-US"
                response.preferredCurrency shouldBe properties.defaultCurrency
            }
        }

        When("getting default user preferences with locale matching a mapping") {
            LocaleContextHolder.setLocale(Locale.forLanguageTag("pl-PL"))
            val response = userPreferencesService.getUserPreferences(userId)

            Then("the default currency should be the one configured in mappings for that locale") {
                response.preferredCurrency shouldBe properties.currencyMappings["pl-PL"]
            }
        }

        When("updating user preferences with valid IETF tag and currency") {
            val updated = userPreferencesService.updateUserPreferences(userId, "pl-PL", "EUR")
            
            Then("the preferences should be updated") {
                updated.language shouldBe "pl-PL"
                updated.preferredCurrency shouldBe "EUR"
                val fetched = userPreferencesService.getUserPreferences(userId)
                fetched.language shouldBe "pl-PL"
                fetched.preferredCurrency shouldBe "EUR"
            }
        }
    }
})
