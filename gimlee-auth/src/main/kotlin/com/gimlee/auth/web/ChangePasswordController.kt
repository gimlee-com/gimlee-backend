package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.auth.user.ChangePasswordService
import com.gimlee.auth.web.dto.request.ChangePasswordRequestDto
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity

@Tag(name = "Authentication")
@RestController
class ChangePasswordController(
    private val changePasswordService: ChangePasswordService,
    private val messageSource: MessageSource
) {
    @Operation(
        summary = "Change Password",
        description = "Allows an authenticated user to change their password."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Password changed successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Incorrect old password. Possible status codes: AUTH_INCORRECT_CREDENTIALS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping(path = ["/auth/changePassword"])
    fun changePassword(
        @Valid @RequestBody changePasswordRequest: ChangePasswordRequestDto
    ): ResponseEntity<StatusResponseDto> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = changePasswordService.changePassword(
            userId,
            changePasswordRequest.oldPassword,
            changePasswordRequest.newPassword
        )

        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message))
    }
}
