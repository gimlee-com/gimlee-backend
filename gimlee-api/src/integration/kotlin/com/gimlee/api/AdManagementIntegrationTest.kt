package com.gimlee.api
import com.gimlee.common.domain.model.Currency

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.web.dto.request.UpdateAdRequestDto
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.time.Instant

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdManagementIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val adService: AdService,
    private val purchaseService: PurchaseService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository
) : BaseIntegrationTest({

    Given("an active ad with some locked stock") {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)

        val addressInfo = WalletAddressInfo(
            type = Currency.ARRR,
            zAddress = "zs1testaddress",
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userWalletAddressRepository.addAddressToUser(sellerId, addressInfo)

        val principal = Principal(userId = sellerId.toHexString(), username = "seller", roles = listOf(Role.USER, Role.PIRATE))
        
        val ad = adService.createAd(sellerId.toHexString(), "Test Ad", 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            description = "Desc",
            price = CurrencyAmount(BigDecimal.TEN, Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        // Lock some stock by initiating a purchase
        val buyerId = ObjectId.get()
        purchaseService.purchase(
            buyerId,
            listOf(PurchaseItemRequestDto(ad.id, 3, BigDecimal.TEN)),
            Currency.ARRR
        )

        val updatedAd = adService.getAd(ad.id)!!
        updatedAd.lockedStock shouldBe 3

        When("the seller attempts to set stock to a level lower than locked stock") {
            // First deactivate it
            mockMvc.post("/sales/ads/${ad.id}/deactivate") {
                requestAttr("principal", principal)
            }.andExpect {
                status { isOk() }
            }

            val updateRequest = UpdateAdRequestDto(
                title = null,
                description = null,
                price = null,
                currency = null,
                location = null,
                mediaPaths = null,
                mainPhotoPath = null,
                stock = 2 // Lower than locked stock 3
            )

            mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principal)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.status") { value("AD_INVALID_AD_STATUS") }
            }
        }

        And("the seller sets stock to a valid level") {
            val updateRequest = UpdateAdRequestDto(
                title = null,
                description = null,
                price = null,
                currency = null,
                location = null,
                mediaPaths = null,
                mainPhotoPath = null,
                stock = 5 // >= locked stock 3
            )

            mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principal)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.stock") { value(5) }
            }
        }
    }
})
