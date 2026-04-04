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
            val updated = userPreferencesService.updateUserPreferences(userId, "pl-PL", "EUR", null)
            
            Then("the preferences should be updated") {
                updated.language shouldBe "pl-PL"
                updated.preferredCurrency shouldBe "EUR"
                val fetched = userPreferencesService.getUserPreferences(userId)
                fetched.language shouldBe "pl-PL"
                fetched.preferredCurrency shouldBe "EUR"
            }
        }

        When("partially updating user preferences") {
            val partiallyUpdated = userPreferencesService.patchUserPreferences(userId, null, "PLN", null)

            Then("only the provided fields should be updated") {
                partiallyUpdated.language shouldBe "pl-PL" // kept from previous step
                partiallyUpdated.preferredCurrency shouldBe "PLN"
                val fetched = userPreferencesService.getUserPreferences(userId)
                fetched.language shouldBe "pl-PL"
                fetched.preferredCurrency shouldBe "PLN"
            }
        }

        When("partially updating user preferences with language only") {
            val partiallyUpdated = userPreferencesService.patchUserPreferences(userId, "en-US", null, null)

            Then("only the language should be updated") {
                partiallyUpdated.language shouldBe "en-US"
                partiallyUpdated.preferredCurrency shouldBe "PLN" // kept from previous step
                val fetched = userPreferencesService.getUserPreferences(userId)
                fetched.language shouldBe "en-US"
                fetched.preferredCurrency shouldBe "PLN"
            }
        }

        When("updating user preferences with country of residence") {
            val updated = userPreferencesService.updateUserPreferences(userId, "en-US", "USD", "US")

            Then("the country of residence should be stored") {
                updated.countryOfResidence shouldBe "US"
                val fetched = userPreferencesService.getUserPreferences(userId)
                fetched.countryOfResidence shouldBe "US"
            }
        }

        When("partially updating only the country of residence") {
            val patched = userPreferencesService.patchUserPreferences(userId, null, null, "PL")

            Then("only the country should change, other fields preserved") {
                patched.language shouldBe "en-US"
                patched.preferredCurrency shouldBe "USD"
                patched.countryOfResidence shouldBe "PL"
            }
        }

        When("partially updating language without affecting country of residence") {
            val patched = userPreferencesService.patchUserPreferences(userId, "pl-PL", null, null)

            Then("the country of residence should be preserved") {
                patched.language shouldBe "pl-PL"
                patched.countryOfResidence shouldBe "PL"
            }
        }
    }
})
