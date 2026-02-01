package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.web.dto.response.AdPreviewDto
import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import com.gimlee.api.web.dto.AdDiscoveryPreviewDto
import com.gimlee.api.web.dto.UserSpaceDetailsDto
import com.gimlee.api.web.dto.UserSpaceDto
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.payments.domain.service.CurrencyConverterService
import java.time.Instant
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.domain.UserOutcome
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.web.dto.response.UserPresenceDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User Spaces", description = "Endpoints for viewing user spaces and their ads")
@RestController
class UserSpaceController(
    private val userService: UserService,
    private val profileService: ProfileService,
    private val adService: AdService,
    private val categoryService: CategoryService,
    private val userPreferencesService: UserPreferencesService,
    private val userPresenceService: UserPresenceService,
    private val currencyConverterService: CurrencyConverterService,
    private val messageSource: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PAGE_SIZE = 60
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Fetch User Space",
        description = "Fetches basic user details and a paginated list of their active ads."
    )
    @ApiResponse(
        responseCode = "200",
        description = "User space with ads",
        content = [Content(schema = Schema(implementation = UserSpaceDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "User not found. Possible status codes: USER_USER_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping(path = ["/spaces/user/{userName}"])
    fun fetchUserSpace(
        @Parameter(description = "Username of the user")
        @PathVariable(name = "userName") userName: String,
        @Parameter(description = "Page number")
        @RequestParam(defaultValue = "0") page: Int
    ): ResponseEntity<Any> {
        val user = userService.findByUsername(userName) ?: return handleOutcome(UserOutcome.USER_NOT_FOUND)
        val userId = user.id!!.toHexString()
        val profile = profileService.getProfile(userId)
        
        val preferredCurrency = getPreferredCurrency()
        val pageOfAds = adService.getAds(
            filters = AdFilters(
                createdBy = userId,
                statuses = listOf(AdStatus.ACTIVE),
                preferredCurrency = preferredCurrency
            ),
            sorting = AdSorting(by = By.CREATED_DATE, direction = Direction.DESC),
            pageRequest = PageRequest.of(page, PAGE_SIZE)
        )

        val categoryIds = pageOfAds.content.mapNotNull { it.categoryId }.toSet()
        val categoryPaths = categoryService.getFullCategoryPaths(categoryIds, LocaleContextHolder.getLocale().toLanguageTag())

        val adsDto = pageOfAds.map { ad ->
            val previewDto = AdPreviewDto.fromAd(ad, ad.categoryId?.let { id -> categoryPaths[id] })
            val preferredPrice = convertPrice(ad.price, preferredCurrency)
            AdDiscoveryPreviewDto.fromAdPreview(previewDto, preferredPrice)
        }

        val presence = userPresenceService.getUserPresence(userId)

        val userDetails = UserSpaceDetailsDto(
            userId = userId,
            username = user.username!!,
            avatarUrl = profile?.avatarUrl,
            presence = UserPresenceDto.fromDomain(presence),
            memberSince = Instant.ofEpochSecond(user.id!!.timestamp.toLong()).toMicros()
        )

        return ResponseEntity.ok(UserSpaceDto(user = userDetails, ads = adsDto))
    }

    private fun getPreferredCurrency(): Currency {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        val currencyCode = if (!principal.isEmptyOrNull()) {
            userPreferencesService.getUserPreferences(principal!!.userId).preferredCurrency
        } else {
            userPreferencesService.getDefaultCurrency()
        }
        return try {
            currencyCode?.let { Currency.valueOf(it) } ?: Currency.USD
        } catch (e: Exception) {
            log.warn("Invalid preferred currency code: {}. Falling back to USD", currencyCode)
            Currency.USD
        }
    }

    private fun convertPrice(price: CurrencyAmount?, to: Currency): CurrencyAmountDto? {
        if (price == null) return null
        return try {
            val result = currencyConverterService.convert(price.amount, price.currency, to)
            CurrencyAmountDto(result.targetAmount, to)
        } catch (e: Exception) {
            log.error("Failed to convert price from {} to {}: {}", price.currency, to, e.message)
            null
        }
    }
}
