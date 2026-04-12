package com.gimlee.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.UUIDv7
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import com.gimlee.purchases.web.dto.request.PurchaseRequestDto
import com.gimlee.user.domain.DeliveryAddressService
import com.gimlee.user.domain.UserPreferencesService
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class DeliveryAddressValidationIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val deliveryAddressService: DeliveryAddressService,
    private val userPreferencesService: UserPreferencesService
) : BaseIntegrationTest({

    Given("a seller with an active Ad") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)

        val addressInfo = WalletAddressInfo(
            type = Currency.ARRR,
            addressType = WalletShieldedAddressType.SAPLING,
            zAddress = "zs1testaddress",
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userWalletAddressRepository.addAddressToUser(sellerId, addressInfo)

        val ad = adService.createAd(sellerId.toHexString(), "Test Item", null, 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            description = "Test Description",
            price = CurrencyAmount(BigDecimal("10.00"), Currency.ARRR),
            settlementCurrencies = setOf(Currency.ARRR),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        fun buildRequest(deliveryAddressId: java.util.UUID) = PurchaseRequestDto(
            items = listOf(PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("10.00"))),
            currency = Currency.ARRR,
            deliveryAddressId = deliveryAddressId
        )

        When("a buyer without country of residence attempts a purchase") {
            val buyerId = ObjectId.get()
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer_no_country", roles = listOf(Role.USER))

            val address = deliveryAddressService.addDeliveryAddress(
                userId = buyerId.toHexString(),
                name = "Home", fullName = "No Country Buyer", street = "123 St",
                city = "Warsaw", postalCode = "00-001", country = "PL",
                phoneNumber = "+48123456789", isDefault = true
            )

            Then("the purchase should fail with COUNTRY_OF_RESIDENCE_REQUIRED") {
                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(buildRequest(address.id))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PURCHASE_COUNTRY_OF_RESIDENCE_REQUIRED") }
                }
            }
        }

        When("a buyer uses a non-existent delivery address") {
            val buyerId = ObjectId.get()
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer_no_addr", roles = listOf(Role.USER))
            userPreferencesService.updateUserPreferences(buyerId.toHexString(), "en-US", "ARRR", "PL")

            Then("the purchase should fail with DELIVERY_ADDRESS_NOT_FOUND") {
                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(buildRequest(UUIDv7.generate()))
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value("PURCHASE_DELIVERY_ADDRESS_NOT_FOUND") }
                }
            }
        }

        When("a buyer uses a delivery address owned by another user") {
            val buyerId = ObjectId.get()
            val otherUserId = ObjectId.get()
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer_wrong_owner", roles = listOf(Role.USER))
            userPreferencesService.updateUserPreferences(buyerId.toHexString(), "en-US", "ARRR", "PL")

            val otherAddress = deliveryAddressService.addDeliveryAddress(
                userId = otherUserId.toHexString(),
                name = "Other Home", fullName = "Other User", street = "456 St",
                city = "Warsaw", postalCode = "00-002", country = "PL",
                phoneNumber = "+48111222333", isDefault = true
            )

            Then("the purchase should fail with DELIVERY_ADDRESS_NOT_FOUND") {
                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(buildRequest(otherAddress.id))
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.status") { value("PURCHASE_DELIVERY_ADDRESS_NOT_FOUND") }
                }
            }
        }

        When("a buyer uses a delivery address from a different country than their residence") {
            val buyerId = ObjectId.get()
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer_country_mismatch", roles = listOf(Role.USER))
            userPreferencesService.updateUserPreferences(buyerId.toHexString(), "en-US", "ARRR", "US")

            val plAddress = deliveryAddressService.addDeliveryAddress(
                userId = buyerId.toHexString(),
                name = "PL Home", fullName = "Country Mismatch Buyer", street = "ul. Nowa 1",
                city = "Warsaw", postalCode = "00-001", country = "PL",
                phoneNumber = "+48123456789", isDefault = true
            )

            Then("the purchase should fail with DELIVERY_ADDRESS_COUNTRY_MISMATCH") {
                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(buildRequest(plAddress.id))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.status") { value("PURCHASE_DELIVERY_ADDRESS_COUNTRY_MISMATCH") }
                }
            }
        }

        When("a buyer uses a valid delivery address matching their country of residence") {
            val buyerId = ObjectId.get()
            val principal = Principal(userId = buyerId.toHexString(), username = "buyer_valid", roles = listOf(Role.USER))
            userPreferencesService.updateUserPreferences(buyerId.toHexString(), "en-US", "ARRR", "PL")

            val validAddress = deliveryAddressService.addDeliveryAddress(
                userId = buyerId.toHexString(),
                name = "PL Home", fullName = "Valid Buyer", street = "ul. Dobra 1",
                city = "Warsaw", postalCode = "00-001", country = "PL",
                phoneNumber = "+48123456789", isDefault = true
            )

            Then("the purchase should succeed") {
                mockMvc.post("/purchases") {
                    requestAttr("principal", principal)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(buildRequest(validAddress.id))
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.purchaseId") { exists() }
                    jsonPath("$.status") { value("AWAITING_PAYMENT") }
                }
            }
        }
    }

    Given("a user with addresses in multiple countries") {
        val userId = ObjectId.get()
        val principal = Principal(userId = userId.toHexString(), username = "multi_country_user", roles = listOf(Role.USER))

        val plAddress = deliveryAddressService.addDeliveryAddress(
            userId = userId.toHexString(),
            name = "PL Home", fullName = "Test User", street = "ul. Nowa 1",
            city = "Warsaw", postalCode = "00-001", country = "PL",
            phoneNumber = "+48123456789", isDefault = true
        )
        val usAddress = deliveryAddressService.addDeliveryAddress(
            userId = userId.toHexString(),
            name = "US Home", fullName = "Test User", street = "123 Main St",
            city = "New York", postalCode = "10001", country = "US",
            phoneNumber = "+1234567890", isDefault = false
        )

        When("the user sets country of residence to PL") {
            userPreferencesService.updateUserPreferences(userId.toHexString(), "en-US", "ARRR", "PL")

            Then("GET /user/delivery-addresses/ should show PL address as active and US address as inactive") {
                mockMvc.get("/user/delivery-addresses/") {
                    requestAttr("principal", principal)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$[?(@.country == 'PL')].active") { value(true) }
                    jsonPath("$[?(@.country == 'US')].active") { value(false) }
                }
            }
        }

        When("the user changes country of residence to US") {
            userPreferencesService.updateUserPreferences(userId.toHexString(), "en-US", "ARRR", "US")

            Then("GET /user/delivery-addresses/ should show US address as active and PL address as inactive") {
                mockMvc.get("/user/delivery-addresses/") {
                    requestAttr("principal", principal)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$[?(@.country == 'US')].active") { value(true) }
                    jsonPath("$[?(@.country == 'PL')].active") { value(false) }
                }
            }
        }
    }
})
