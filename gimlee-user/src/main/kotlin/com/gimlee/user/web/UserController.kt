package com.gimlee.user.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.user.domain.DeliveryAddressService
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.domain.UserOutcome
import com.gimlee.user.web.dto.request.AddDeliveryAddressRequestDto
import com.gimlee.user.web.dto.request.PatchUserPreferencesRequestDto
import com.gimlee.user.web.dto.request.UpdateUserPreferencesRequestDto
import com.gimlee.user.web.dto.response.DeliveryAddressDto
import com.gimlee.user.web.dto.response.UserPreferencesDto
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "User Profile", description = "Endpoints for managing user preferences and profile settings")
@RestController
@RequestMapping("/user")
class UserController(
    private val deliveryAddressService: DeliveryAddressService,
    private val userPreferencesService: UserPreferencesService,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Get User Preferences",
        description = "Retrieves preferences (e.g., language) for the authenticated user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Preferences retrieved successfully",
        content = [Content(schema = Schema(implementation = UserPreferencesDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/preferences")
    @Privileged(role = "USER")
    fun getUserPreferences(): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} retrieving preferences", principal.userId)

        return try {
            val preferences = userPreferencesService.getUserPreferences(principal.userId)
            ResponseEntity.ok(UserPreferencesDto.fromDomain(preferences))
        } catch (e: Exception) {
            log.error("Error retrieving preferences for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Update User Preferences",
        description = "Updates user preferences (e.g., language, preferred currency). Language must follow IETF standard (e.g., en-US). Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Preferences updated successfully",
        content = [Content(schema = Schema(implementation = UserPreferencesDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/preferences")
    @Privileged(role = "USER")
    fun updateUserPreferences(@Valid @RequestBody request: UpdateUserPreferencesRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} updating preferences", principal.userId)

        return try {
            val preferences = userPreferencesService.updateUserPreferences(
                principal.userId,
                request.language,
                request.preferredCurrency
            )
            ResponseEntity.ok(UserPreferencesDto.fromDomain(preferences))
        } catch (e: Exception) {
            log.error("Error updating preferences for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Partially Update User Preferences",
        description = "Partially updates user preferences (e.g., language, preferred currency). All fields are optional. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Preferences updated successfully",
        content = [Content(schema = Schema(implementation = UserPreferencesDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PatchMapping("/preferences")
    @Privileged(role = "USER")
    fun patchUserPreferences(@Valid @RequestBody request: PatchUserPreferencesRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} partially updating preferences", principal.userId)

        return try {
            val preferences = userPreferencesService.patchUserPreferences(
                principal.userId,
                request.language,
                request.preferredCurrency
            )
            ResponseEntity.ok(UserPreferencesDto.fromDomain(preferences))
        } catch (e: Exception) {
            log.error("Error partially updating preferences for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Add a Delivery Address",
        description = "Adds a new delivery address to the user's profile. Up to 50 addresses allowed. Requires USER role."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Address added successfully",
        content = [Content(schema = Schema(implementation = DeliveryAddressDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request or maximum addresses reached. Possible status codes: USER_MAX_ADDRESSES_REACHED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/delivery-addresses")
    @Privileged(role = "USER")
    fun addDeliveryAddress(@Valid @RequestBody request: AddDeliveryAddressRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} attempting to add a new delivery address", principal.userId)

        return try {
            val address = deliveryAddressService.addDeliveryAddress(
                userId = principal.userId,
                name = request.name,
                fullName = request.fullName,
                street = request.street,
                city = request.city,
                postalCode = request.postalCode,
                country = request.country,
                phoneNumber = request.phoneNumber,
                isDefault = request.isDefault
            )
            ResponseEntity.status(HttpStatus.CREATED).body(DeliveryAddressDto.fromDomain(address))
        } catch (e: DeliveryAddressService.MaxAddressesReachedException) {
            log.warn("User {} reached maximum delivery addresses", principal.userId)
            handleOutcome(UserOutcome.MAX_ADDRESSES_REACHED)
        } catch (e: Exception) {
            log.error("Error adding delivery address for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }

    @Operation(
        summary = "Get Delivery Addresses",
        description = "Retrieves all delivery addresses for the authenticated user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Addresses retrieved successfully",
        content = [Content(array = ArraySchema(schema = Schema(implementation = DeliveryAddressDto::class)))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/delivery-addresses/")
    @Privileged(role = "USER")
    fun getDeliveryAddresses(): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} retrieving delivery addresses", principal.userId)

        return try {
            val addresses = deliveryAddressService.getDeliveryAddresses(principal.userId)
            ResponseEntity.ok(addresses.map { DeliveryAddressDto.fromDomain(it) })
        } catch (e: Exception) {
            log.error("Error retrieving delivery addresses for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }
}
