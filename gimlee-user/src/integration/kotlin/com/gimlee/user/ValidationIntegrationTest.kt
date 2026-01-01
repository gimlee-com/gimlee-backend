package com.gimlee.user
    
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.web.dto.request.AddDeliveryAddressRequestDto
import com.gimlee.user.web.dto.request.UpdateUserPreferencesRequestDto
import io.kotest.matchers.shouldBe
import jakarta.validation.Validator
import org.springframework.beans.factory.annotation.Autowired

class ValidationIntegrationTest(
    @Autowired private val validator: Validator
) : BaseIntegrationTest({

    Given("an AddDeliveryAddressRequestDto") {
        When("validating with a 3-letter country code") {
            val dto = AddDeliveryAddressRequestDto(
                name = "Home",
                fullName = "John Doe",
                street = "Street",
                city = "City",
                postalCode = "12345",
                country = "USA", // Invalid, should be US
                phoneNumber = "123456789"
            )
            val violations = validator.validate(dto)
            
            Then("it should have a validation error for country") {
                violations.any { it.propertyPath.toString() == "country" } shouldBe true
            }
        }

        When("validating with a valid 2-letter country code") {
            val dto = AddDeliveryAddressRequestDto(
                name = "Home",
                fullName = "John Doe",
                street = "Street",
                city = "City",
                postalCode = "12345",
                country = "US",
                phoneNumber = "123456789"
            )
            val violations = validator.validate(dto)
            
            Then("it should have no validation errors") {
                violations.isEmpty() shouldBe true
            }
        }
    }

    Given("an UpdateUserPreferencesRequestDto") {
        When("validating with an invalid IETF language tag") {
            val dto = UpdateUserPreferencesRequestDto(language = "en_US") // Invalid, should be en-US
            val violations = validator.validate(dto)
            
            Then("it should have a validation error for language") {
                violations.any { it.propertyPath.toString() == "language" } shouldBe true
            }
        }

        When("validating with a non-existent language code") {
            val dto = UpdateUserPreferencesRequestDto(language = "xx-US")
            val violations = validator.validate(dto)
            
            Then("it should have a validation error for language") {
                violations.any { it.propertyPath.toString() == "language" } shouldBe true
            }
        }

        When("validating with a valid IETF language tag") {
            val dto = UpdateUserPreferencesRequestDto(language = "en-US")
            val violations = validator.validate(dto)
            
            Then("it should have no validation errors") {
                violations.isEmpty() shouldBe true
            }
        }
    }
})
