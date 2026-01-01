package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.exception.AuthenticationException
import com.gimlee.auth.util.extractToken
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Authentication")
@RestController
class InitSessionController {
    @Operation(
        summary = "Init Session",
        description = "Checks the current authentication status based on the JWT cookie."
    )
    @ApiResponse(responseCode = "200", description = "Session status returned")
    @GetMapping(path = ["/auth/session/init"])
    fun init(
        request: HttpServletRequest
    ): IdentityVerificationResponse {
        return try {
            IdentityVerificationResponse(success = true, accessToken = extractToken(request))
        } catch (e: AuthenticationException) {
            IdentityVerificationResponse(success = true, accessToken = null)
        }

    }
}