package com.gimlee.user.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.web.dto.request.UpdateAvatarRequestDto
import com.gimlee.user.web.dto.response.UserProfileDto
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

@Tag(name = "User Profile", description = "Endpoints for managing user profile")
@RestController
@RequestMapping("/user/profile")
class ProfileController(
    private val profileService: ProfileService,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Update User Avatar",
        description = "Updates or sets the avatar URL for the authenticated user. Requires USER role."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Avatar updated successfully",
        content = [Content(schema = Schema(implementation = UserProfileDto::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error. Possible status codes: INTERNAL_ERROR",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/avatar")
    @Privileged(role = "USER")
    fun updateAvatar(@Valid @RequestBody request: UpdateAvatarRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        log.info("User {} updating avatar", principal.userId)

        return try {
            val profile = profileService.updateAvatar(principal.userId, request.avatarUrl)
            ResponseEntity.ok(UserProfileDto.fromDomain(profile))
        } catch (e: Exception) {
            log.error("Error updating avatar for user {}: {}", principal.userId, e.message, e)
            handleOutcome(CommonOutcome.INTERNAL_ERROR)
        }
    }
}
