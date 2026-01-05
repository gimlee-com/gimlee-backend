package com.gimlee.ads.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.web.dto.request.UpdateAdRequestDto
import com.gimlee.ads.web.dto.response.AdDto
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import java.math.BigDecimal
import java.util.*

class ManageAdControllerTest : StringSpec({

    val adService = mockk<AdService>()
    val messageSource = mockk<MessageSource>()
    val controller = ManageAdController(adService, messageSource)

    beforeTest {
        mockkStatic(RequestContextHolder::class)
        mockkObject(AdDto.Companion)
        val requestAttributes = mockk<RequestAttributes>()
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
        every { messageSource.getMessage(any(), any(), any()) } returns "Mocked Message"
    }

    afterTest {
        unmockkAll()
    }

    "updateAd should return 403 when using ARRR without PIRATE role" {
        val principal = Principal(userId = "user1", username = "user1", roles = listOf(Role.USER))
        every { RequestContextHolder.getRequestAttributes()!!.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal

        val request = UpdateAdRequestDto(
            title = "Test",
            description = null,
            price = BigDecimal("10"),
            currency = Currency.ARRR,
            location = null,
            mediaPaths = null,
            mainPhotoPath = null,
            stock = 1
        )

        val response = controller.updateAd("ad1", request)

        response.statusCode shouldBe HttpStatus.FORBIDDEN
        val body = response.body as StatusResponseDto
        body.status shouldBe "AD_PIRATE_ROLE_REQUIRED"
        body.success shouldBe false
    }

    "updateAd should proceed when using ARRR with PIRATE role" {
        val principal = Principal(userId = "user1", username = "user1", roles = listOf(Role.USER, Role.PIRATE))
        every { RequestContextHolder.getRequestAttributes()!!.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal
        
        val updatedAd = mockk<com.gimlee.ads.domain.model.Ad>(relaxed = true)
        every { adService.updateAd(any(), any(), any()) } returns updatedAd
        every { AdDto.fromDomain(any()) } returns mockk()

        val request = UpdateAdRequestDto(
            title = "Test",
            description = null,
            price = BigDecimal("10"),
            currency = Currency.ARRR,
            location = null,
            mediaPaths = null,
            mainPhotoPath = null,
            stock = 1
        )

        val response = controller.updateAd("ad1", request)

        response.statusCode shouldBe HttpStatus.OK
    }
    
    "updateAd should proceed when using USD without PIRATE role" {
        val principal = Principal(userId = "user1", username = "user1", roles = listOf(Role.USER))
        every { RequestContextHolder.getRequestAttributes()!!.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) } returns principal
        
        val updatedAd = mockk<com.gimlee.ads.domain.model.Ad>(relaxed = true)
        every { adService.updateAd(any(), any(), any()) } returns updatedAd
        every { AdDto.fromDomain(any()) } returns mockk()

        val request = UpdateAdRequestDto(
            title = "Test",
            description = null,
            price = BigDecimal("10"),
            currency = Currency.USD,
            location = null,
            mediaPaths = null,
            mainPhotoPath = null,
            stock = 1
        )

        val response = controller.updateAd("ad1", request)

        response.statusCode shouldBe HttpStatus.OK
    }
})
