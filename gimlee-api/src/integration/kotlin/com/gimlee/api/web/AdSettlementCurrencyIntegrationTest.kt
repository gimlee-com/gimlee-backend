package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.web.dto.StatusResponseDto
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.math.BigDecimal

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdSettlementCurrencyIntegrationTest(
    private val mockMvc: MockMvc,
    private val adService: AdService,
    private val objectMapper: ObjectMapper,
    private val userRoleRepository: UserRoleRepository
) : BaseIntegrationTest({

    Given("an ad in INACTIVE state") {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.PIRATE)

        val principal = Principal(userId = userId.toHexString(), username = "testuser", roles = listOf(Role.USER, Role.PIRATE))
        val ad = adService.createAd(userId.toHexString(), "Test Ad", null, 10)

        When("attempting to set a non-settlement currency (USD) via API") {
            val updateRequest = mapOf(
                "price" to 100,
                "currency" to "USD"
            )

            val result = mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principal)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isBadRequest() }
            }.andReturn()

            val content = result.response.contentAsString
            val response = objectMapper.readValue(content, StatusResponseDto::class.java)

            Then("it should return a bad request status with AD_INVALID_OPERATION") {
                response.status shouldBe "AD_INVALID_OPERATION"
                response.message?.contains("Currency USD is not allowed for settlement.") shouldBe true
            }
        }
        
        When("attempting to set a settlement currency (ARRR) via API with PIRATE role") {
            val updateRequest = mapOf(
                "price" to 100,
                "currency" to "ARRR"
            )

            mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principal)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isOk() }
            }

            Then("it should succeed") {
                val updatedAd = adService.getAd(ad.id)
                updatedAd?.price?.currency shouldBe Currency.ARRR
            }
        }

        When("attempting to set a settlement currency (ARRR) via API without PIRATE role") {
            val userWithoutPirateId = ObjectId.get()
            userRoleRepository.add(userWithoutPirateId, Role.USER)
            val principalWithoutPirate = Principal(userId = userWithoutPirateId.toHexString(), username = "nopirate", roles = listOf(Role.USER))
            val ad2 = adService.createAd(userWithoutPirateId.toHexString(), "Test Ad 2", null, 10)

            val updateRequest = mapOf(
                "price" to 100,
                "currency" to "ARRR"
            )

            val result = mockMvc.put("/sales/ads/${ad2.id}") {
                requestAttr("principal", principalWithoutPirate)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isForbidden() }
            }.andReturn()

            Then("it should return a forbidden status with AD_PIRATE_ROLE_REQUIRED") {
                val response = objectMapper.readValue(result.response.contentAsString, StatusResponseDto::class.java)
                response.status shouldBe "AD_PIRATE_ROLE_REQUIRED"
            }
        }

        When("attempting to set a settlement currency (YEC) via API without YCASH role") {
            // principal has PIRATE, but not YCASH. userId already has PIRATE in DB.
            val updateRequest = mapOf(
                "price" to 100,
                "currency" to "YEC"
            )

            val result = mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principal)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isForbidden() }
            }.andReturn()

            Then("it should return a forbidden status with AD_YCASH_ROLE_REQUIRED") {
                val response = objectMapper.readValue(result.response.contentAsString, StatusResponseDto::class.java)
                response.status shouldBe "AD_YCASH_ROLE_REQUIRED"
            }
        }

        When("attempting to set a settlement currency (YEC) via API with YCASH role") {
            userRoleRepository.add(userId, Role.YCASH)
            val principalWithYcash = Principal(userId = userId.toHexString(), username = "testuser", roles = listOf(Role.USER, Role.PIRATE, Role.YCASH))
            
            val updateRequest = mapOf(
                "price" to 100,
                "currency" to "YEC"
            )

            mockMvc.put("/sales/ads/${ad.id}") {
                requestAttr("principal", principalWithYcash)
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isOk() }
            }

            Then("it should succeed") {
                val updatedAd = adService.getAd(ad.id)
                updatedAd?.price?.currency shouldBe Currency.YEC
            }
        }
    }
})
