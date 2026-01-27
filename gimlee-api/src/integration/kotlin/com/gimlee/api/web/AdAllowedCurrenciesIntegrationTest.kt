package com.gimlee.api.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.gimlee.ads.web.dto.response.CurrencyInfoDto
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class AdAllowedCurrenciesIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRoleRepository: UserRoleRepository,
    private val objectMapper: ObjectMapper
) : BaseIntegrationTest({

    Given("users with different roles") {
        val userWithPirateId = ObjectId.get()
        userRoleRepository.add(userWithPirateId, Role.USER)
        userRoleRepository.add(userWithPirateId, Role.PIRATE)
        val piratePrincipal = Principal(userId = userWithPirateId.toHexString(), username = "pirate", roles = listOf(Role.USER, Role.PIRATE))

        val userWithYcashId = ObjectId.get()
        userRoleRepository.add(userWithYcashId, Role.USER)
        userRoleRepository.add(userWithYcashId, Role.YCASH)
        val ycashPrincipal = Principal(userId = userWithYcashId.toHexString(), username = "ycash", roles = listOf(Role.USER, Role.YCASH))

        val userWithBothId = ObjectId.get()
        userRoleRepository.add(userWithBothId, Role.USER)
        userRoleRepository.add(userWithBothId, Role.PIRATE)
        userRoleRepository.add(userWithBothId, Role.YCASH)
        val bothPrincipal = Principal(userId = userWithBothId.toHexString(), username = "both", roles = listOf(Role.USER, Role.PIRATE, Role.YCASH))

        val userWithNoneId = ObjectId.get()
        userRoleRepository.add(userWithNoneId, Role.USER)
        val nonePrincipal = Principal(userId = userWithNoneId.toHexString(), username = "none", roles = listOf(Role.USER))

        When("user with PIRATE role fetches allowed currencies") {
            val result = mockMvc.get("/sales/ads/allowed-currencies") {
                requestAttr("principal", piratePrincipal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val currencies: List<CurrencyInfoDto> = objectMapper.readValue(result.response.contentAsString)

            Then("it should only contain ARRR with localized names") {
                currencies shouldContainExactlyInAnyOrder listOf(
                    CurrencyInfoDto(Currency.ARRR, "Pirate Chain"),
                )
            }
        }

        When("user with YCASH role fetches allowed currencies") {
            val result = mockMvc.get("/sales/ads/allowed-currencies") {
                requestAttr("principal", ycashPrincipal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val currencies: List<CurrencyInfoDto> = objectMapper.readValue(result.response.contentAsString)

            Then("it should only contain YEC with localized names") {
                currencies shouldContainExactlyInAnyOrder listOf(
                    CurrencyInfoDto(Currency.YEC, "YCash")
                )
            }
        }

        When("user with both roles fetches allowed currencies") {
            val result = mockMvc.get("/sales/ads/allowed-currencies") {
                requestAttr("principal", bothPrincipal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val currencies: List<CurrencyInfoDto> = objectMapper.readValue(result.response.contentAsString)

            Then("it should contain ARRR and YEC with localized names") {
                currencies shouldContainExactlyInAnyOrder listOf(
                    CurrencyInfoDto(Currency.ARRR, "Pirate Chain"),
                    CurrencyInfoDto(Currency.YEC, "YCash")
                )
            }
        }

        When("user with no roles fetches allowed currencies") {
            val result = mockMvc.get("/sales/ads/allowed-currencies") {
                requestAttr("principal", nonePrincipal)
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val currencies: List<CurrencyInfoDto> = objectMapper.readValue(result.response.contentAsString)

            Then("it should be empty") {
                currencies shouldBe emptyList<CurrencyInfoDto>()
            }
        }
    }
})
