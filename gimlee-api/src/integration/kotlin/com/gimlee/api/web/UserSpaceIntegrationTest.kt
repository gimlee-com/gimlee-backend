package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.user.domain.ProfileService
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = ["gimlee.auth.jwt.enabled=false"])
class UserSpaceIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val adService: AdService,
    private val profileService: ProfileService
) : BaseIntegrationTest({

    Given("a user with profile and ads") {
        val userId = ObjectId.get()
        val username = "testpirate"
        userRepository.save(User(id = userId, username = username))
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.PIRATE)
        profileService.updateAvatar(userId.toHexString(), "http://avatar.url")
        
        val ad = adService.createAd(userId.toHexString(), "Pirate Treasure", null, 1)
        adService.updateAd(ad.id, userId.toHexString(), UpdateAdRequest(
            description = "Test Description",
            price = CurrencyAmount(BigDecimal("1000"), Currency.ARRR),
            location = com.gimlee.ads.domain.model.Location("city", doubleArrayOf(0.0, 0.0)),
            stock = 1
        ))
        adService.activateAd(ad.id, userId.toHexString())

        When("fetching the user space") {
            val result = mockMvc.get("/spaces/user/$username") {
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val content = result.response.contentAsString

            Then("it should return user details and ads") {
                content.contains("\"username\":\"$username\"") shouldBe true
                content.contains("\"avatarUrl\":\"http://avatar.url\"") shouldBe true
                content.contains("\"memberSince\":") shouldBe true
                content.contains("\"title\":\"Pirate Treasure\"") shouldBe true
            }
        }

        When("fetching a non-existent user space") {
            val result = mockMvc.get("/spaces/user/nobody") {
            }.andExpect {
                status { isNotFound() }
            }.andReturn()

            Then("it should return 404 with USER_USER_NOT_FOUND") {
                val content = result.response.contentAsString
                content.contains("\"status\":\"USER_USER_NOT_FOUND\"") shouldBe true
            }
        }
    }
})
