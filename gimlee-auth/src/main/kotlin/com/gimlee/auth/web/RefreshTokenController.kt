package com.gimlee.auth.web

import com.gimlee.auth.domain.auth.RefreshResult
import com.gimlee.auth.model.Principal
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.auth.service.RefreshTokenService
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.web.dto.request.RefreshTokenRequestDto
import com.gimlee.auth.web.dto.request.RevokeSessionRequestDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.bson.types.ObjectId
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

@Tag(name = "Authentication", description = "Endpoints for token refresh and session management")
@RestController
class RefreshTokenController(
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val messageSource: MessageSource
) {
    @Operation(
        summary = "Refresh Access Token",
        description = "Exchanges a valid refresh token for a new access token and rotated refresh token. " +
            "The old refresh token is invalidated. If a previously-rotated token is reused, all sessions in " +
            "that token family are revoked as a security measure. Rate limited to prevent abuse."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Token refresh result. Possible status codes: SUCCESS, AUTH_REFRESH_TOKEN_INVALID, " +
            "AUTH_REFRESH_TOKEN_EXPIRED, AUTH_REFRESH_TOKEN_REVOKED, AUTH_REFRESH_TOKEN_REUSE_DETECTED, " +
            "AUTH_REFRESH_TOKEN_DEVICE_MISMATCH. Note: This endpoint is rate-limited at the load balancer (429)."
    )
    @PostMapping(path = ["/auth/token/refresh"])
    fun refresh(@RequestBody request: RefreshTokenRequestDto): ResponseEntity<IdentityVerificationResponse> {
        return when (val result = refreshTokenService.rotateRefreshToken(request.refreshToken, request.deviceId)) {
            is RefreshResult.Success -> {
                val user = userRepository.findOneByField(User.FIELD_ID, ObjectId(result.userId))
                val roles = userRoleRepository.getAll(ObjectId(result.userId))
                val accessToken = jwtTokenService.generateToken(result.userId, user!!.username!!, roles)
                val outcome = CommonOutcome.SUCCESS
                ResponseEntity.ok(
                    IdentityVerificationResponse(
                        success = true,
                        status = outcome.code,
                        message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale()),
                        accessToken = accessToken,
                        refreshToken = result.newPlaintextToken
                    )
                )
            }
            is RefreshResult.Failure -> {
                ResponseEntity.status(result.outcome.httpCode).body(
                    IdentityVerificationResponse(
                        success = false,
                        status = result.outcome.code,
                        message = messageSource.getMessage(result.outcome.messageKey, null, LocaleContextHolder.getLocale())
                    )
                )
            }
        }
    }

    @Operation(
        summary = "Revoke Current Session",
        description = "Revokes the refresh token provided in the request body, effectively logging out the current device session."
    )
    @ApiResponse(responseCode = "200", description = "Session revoked successfully")
    @PostMapping(path = ["/auth/sessions/revoke"])
    fun revokeSession(@RequestBody request: RevokeSessionRequestDto): ResponseEntity<StatusResponseDto> {
        val principal = RequestContextHolder.getRequestAttributes()!!
            .getAttribute("principal", RequestAttributes.SCOPE_REQUEST) as Principal

        val outcome = if (refreshTokenService.revokeSession(request.refreshToken, principal.userId)) {
            CommonOutcome.SUCCESS
        } else {
            com.gimlee.auth.domain.AuthOutcome.REFRESH_TOKEN_INVALID
        }
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message))
    }

    @Operation(
        summary = "Revoke All Sessions",
        description = "Revokes all refresh tokens for the authenticated user, logging them out from all devices."
    )
    @ApiResponse(responseCode = "200", description = "All sessions revoked successfully")
    @PostMapping(path = ["/auth/sessions/revoke-all"])
    fun revokeAllSessions(): ResponseEntity<StatusResponseDto> {
        val principal = RequestContextHolder.getRequestAttributes()!!
            .getAttribute("principal", RequestAttributes.SCOPE_REQUEST) as Principal

        refreshTokenService.revokeAllSessions(principal.userId)
        val outcome = CommonOutcome.SUCCESS
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.ok(StatusResponseDto.fromOutcome(outcome, message))
    }
}
