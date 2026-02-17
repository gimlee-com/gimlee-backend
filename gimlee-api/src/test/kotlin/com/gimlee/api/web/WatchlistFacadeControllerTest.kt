package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.WatchlistService
import com.gimlee.api.service.AdEnrichmentService
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.Role
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class WatchlistFacadeControllerTest : StringSpec({
    val watchlistService = mockk<WatchlistService>()
    val adService = mockk<AdService>()
    val adEnrichmentService = mockk<AdEnrichmentService>()
    
    val controller = WatchlistFacadeController(
        watchlistService,
        adService,
        adEnrichmentService
    )

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

    "getMyWatchedAds should return empty list when watchlist is empty" {
        val userId = "user1"
        mockPrincipal(userId)
        
        every { watchlistService.getWatchlist(userId) } returns emptyList()

        val response = controller.getMyWatchedAds()

        response.statusCode shouldBe HttpStatus.OK
        response.body!!.isEmpty() shouldBe true
        
        verify(exactly = 0) { adService.getAds(any()) }
    }
    
    "getMyWatchedAds should return enriched ads" {
        val userId = "user1"
        val adId = "ad1"
        
        // Mock Principal
        val principal = Principal(userId = userId, username = "user", roles = listOf(Role.USER))
        val requestAttributes = mockk<RequestAttributes>(relaxed = true)
        every { requestAttributes.getAttribute("principal", 0) } returns principal
        every { RequestContextHolder.getRequestAttributes() } returns requestAttributes
        
        every { watchlistService.getWatchlist(userId) } returns listOf(adId)
        val ad = mockk<com.gimlee.ads.domain.model.Ad>()
        val ads = listOf(ad)
        every { adService.getAds(listOf(adId)) } returns ads
        
        val enrichedDto = mockk<AdDiscoveryPreviewDto>()
        every { adEnrichmentService.enrichAdPreviews(ads, userId, any()) } returns listOf(enrichedDto)

        val response = controller.getMyWatchedAds()

        response.statusCode shouldBe HttpStatus.OK
        response.body shouldBe listOf(enrichedDto)
    }
})