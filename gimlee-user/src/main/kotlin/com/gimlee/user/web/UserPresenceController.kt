package com.gimlee.user.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.web.dto.request.UpdateUserPresenceRequestDto
import com.gimlee.user.web.dto.response.UserPresenceDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "User Presence", description = "Endpoints for managing user activity and status")
@RestController
@RequestMapping("/user")
class UserPresenceController(
    private val userPresenceService: UserPresenceService,
    private val messageSource: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Ping",
        description = "Notifies the server that the user is still active. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Activity tracked successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/ping")
    @Privileged(role = "USER")
    fun ping(): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        userPresenceService.trackActivity(userId)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Get My Presence",
        description = "Retrieves the current presence status of the authenticated user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Presence retrieved successfully",
        content = [Content(schema = Schema(implementation = UserPresenceDto::class))]
    )
    @GetMapping("/me/presence")
    @Privileged(role = "USER")
    fun getMyPresence(): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val presence = userPresenceService.getUserPresence(userId)
        return ResponseEntity.ok(UserPresenceDto.fromDomain(presence))
    }

    @Operation(
        summary = "Update My Presence",
        description = "Updates the presence status and optional custom message for the authenticated user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Presence updated successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PatchMapping("/me/presence")
    @Privileged(role = "USER")
    fun updateMyPresence(@Valid @RequestBody request: UpdateUserPresenceRequestDto): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        userPresenceService.updateStatus(userId, request.status, request.customStatus)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Get User Presence",
        description = "Retrieves the presence status of a specific user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Presence retrieved successfully",
        content = [Content(schema = Schema(implementation = UserPresenceDto::class))]
    )
    @GetMapping("/{userId}/presence")
    @Privileged(role = "USER")
    fun getUserPresence(@PathVariable userId: String): ResponseEntity<Any> {
        val presence = userPresenceService.getUserPresence(userId)
        return ResponseEntity.ok(UserPresenceDto.fromDomain(presence))
    }
}
