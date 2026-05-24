package com.gimlee.auth.web

import com.gimlee.auth.annotation.AllowUserStatus
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.model.Principal
import com.gimlee.auth.service.RefreshTokenService
import com.gimlee.auth.web.dto.request.RevokeSessionRequestDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.user.LoginService
import com.gimlee.auth.web.dto.request.LoginRequestDto
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

@Tag(name = "Authentication", description = "Endpoints for user login and logout")
@RestController
class LoginController(
    private val loginService: LoginService,
    private val refreshTokenService: RefreshTokenService,
    private val messageSource: MessageSource
) {
    @Operation(
        summary = "Login User",
        description = "Authenticates a user and returns an access token and a refresh token. " +
            "The access token is short-lived (configurable, default 15 minutes). " +
            "The refresh token is long-lived (configurable, default 30 days) and should be used to obtain new access tokens."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Login successful or failed. Possible status codes: SUCCESS, AUTH_INCORRECT_CREDENTIALS"
    )
    @PostMapping(path = ["/auth/login"])
    fun login(
        @RequestBody loginData: LoginRequestDto
    ): IdentityVerificationResponse {
        return loginService.login(loginData.username, loginData.password, loginData.deviceId)
    }

    @Operation(
        summary = "Logout User",
        description = "Logs out the current user by revoking the provided refresh token, ending the device session."
    )
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @AllowUserStatus(UserStatus.BANNED)
    @PostMapping(path = ["/auth/logout"])
    fun logout(@RequestBody(required = false) request: RevokeSessionRequestDto?): ResponseEntity<StatusResponseDto> {
        if (request?.refreshToken != null) {
            val principal = RequestContextHolder.getRequestAttributes()!!
                .getAttribute("principal", RequestAttributes.SCOPE_REQUEST) as Principal
            refreshTokenService.revokeSession(request.refreshToken, principal.userId)
        }
        val outcome = CommonOutcome.SUCCESS
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.ok(StatusResponseDto.fromOutcome(outcome, message))
    }
}