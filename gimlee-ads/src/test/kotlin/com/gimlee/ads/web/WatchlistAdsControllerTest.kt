package com.gimlee.ads.web

import com.gimlee.ads.domain.AdOutcome
import com.gimlee.ads.domain.WatchlistService
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import com.gimlee.common.web.dto.StatusResponseDto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.springframework.context.MessageSource
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class WatchlistAdsControllerTest : StringSpec({

    val watchlistService = mockk<WatchlistService>(relaxed = true)
    val messageSource = mockk<MessageSource>(relaxed = true)
    val controller = WatchlistAdsController(watchlistService, messageSource)

    beforeTest {
        mockkStatic(RequestContextHolder::class)
        val requestAttributes = mockk<RequestAttributes>(relaxed = true)
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
    }

    afterTest {
        unmockkAll()
    }
    
    fun mockPrincipal(userId: String) {
        val principal = Principal(userId = userId, username = "user", roles = listOf(Role.USER))
        every { 
            RequestContextHolder.getRequestAttributes()?.getAttribute("principal", RequestAttributes.SCOPE_REQUEST) 
        } returns principal
    }

    "addToWatchlist should return success" {
        mockPrincipal("user1")
        val adId = "ad1"
        
        every { messageSource.getMessage(AdOutcome.ADDED_TO_WATCHLIST.messageKey, null, any()) } returns "Added"

        val response = controller.addToWatchlist(adId)

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe StatusResponseDto(true, AdOutcome.ADDED_TO_WATCHLIST.code, "Added")
        
        verify { watchlistService.addToWatchlist("user1", adId) }
    }

    "removeFromWatchlist should return success" {
        mockPrincipal("user1")
        val adId = "ad1"
        
        every { messageSource.getMessage(AdOutcome.REMOVED_FROM_WATCHLIST.messageKey, null, any()) } returns "Removed"

        val response = controller.removeFromWatchlist(adId)

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe StatusResponseDto(true, AdOutcome.REMOVED_FROM_WATCHLIST.code, "Removed")
        
        verify { watchlistService.removeFromWatchlist("user1", adId) }
    }
})
