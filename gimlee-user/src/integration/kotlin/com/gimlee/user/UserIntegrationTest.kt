package com.gimlee.user
    
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.user.domain.DeliveryAddressService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import org.bson.types.ObjectId

class UserIntegrationTest(
    private val deliveryAddressService: DeliveryAddressService
) : BaseIntegrationTest({

    Given("a user") {
        val userId = ObjectId.get().toHexString()

        When("the user adds a delivery address") {
            val address = deliveryAddressService.addDeliveryAddress(
                userId = userId,
                name = "Home",
                fullName = "John Doe",
                street = "123 Main St",
                city = "New York",
                postalCode = "10001",
                country = "US",
                phoneNumber = "+1234567890",
                isDefault = true
            )

            Then("the address should be saved correctly") {
                address.name shouldBe "Home"
                address.userId shouldBe userId
                address.isDefault shouldBe true
            }

            Then("it should be retrievable") {
                val addresses = deliveryAddressService.getDeliveryAddresses(userId)
                addresses shouldHaveSize 1
                addresses[0].id shouldBe address.id
            }
        }

        When("the user adds a second address as default") {
             deliveryAddressService.addDeliveryAddress(
                userId = userId,
                name = "Work",
                fullName = "John Doe",
                street = "456 Office Rd",
                city = "New York",
                postalCode = "10002",
                country = "US",
                phoneNumber = "+1234567890",
                isDefault = true
            )

            Then("the first address should no longer be default") {
                val addresses = deliveryAddressService.getDeliveryAddresses(userId)
                addresses shouldHaveSize 2
                val home = addresses.find { it.name == "Home" }!!
                val work = addresses.find { it.name == "Work" }!!
                
                home.isDefault shouldBe false
                work.isDefault shouldBe true
            }
        }
        
        When("the user tries to add more than 50 addresses") {
            val addressesBefore = deliveryAddressService.getDeliveryAddresses(userId)
            val currentCount = addressesBefore.size
            val remaining = 50 - currentCount
            
            repeat(remaining) { i ->
                deliveryAddressService.addDeliveryAddress(
                    userId = userId,
                    name = "Address $i",
                    fullName = "John Doe",
                    street = "Street $i",
                    city = "City",
                    postalCode = "12345",
                    country = "US",
                    phoneNumber = "123",
                    isDefault = false
                )
            }
            
            Then("adding one more should throw an exception") {
                val exception = io.kotest.assertions.throwables.shouldThrow<DeliveryAddressService.MaxAddressesReachedException> {
                    deliveryAddressService.addDeliveryAddress(
                        userId = userId,
                        name = "One Too Many",
                        fullName = "John Doe",
                        street = "Street 51",
                        city = "City",
                        postalCode = "12345",
                        country = "US",
                        phoneNumber = "123",
                        isDefault = false
                    )
                }
                exception.message shouldBe "Maximum number of delivery addresses reached (50)"
            }
        }
    }
})
