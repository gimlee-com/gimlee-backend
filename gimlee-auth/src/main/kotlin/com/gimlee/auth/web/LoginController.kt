package com.gimlee.auth.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.user.LoginService
import com.gimlee.auth.web.dto.request.LoginRequestDto

@Tag(name = "Authentication", description = "Endpoints for user login and logout")
@RestController
class LoginController(
    private val loginService: LoginService
) {
    @Operation(
        summary = "Login User",
        description = "Authenticates a user and returns a session token (usually via a JWT cookie). If the user is unverified, the response will indicate that verification is required."
    )
    @ApiResponse(responseCode = "200", description = "Login successful or verification required")
    @PostMapping(path = ["/auth/login"])
    fun login(
        @RequestBody loginData: LoginRequestDto
    ): IdentityVerificationResponse {
        return loginService.login(loginData.username, loginData.password)
    }

    @Operation(summary = "Logout User", description = "Logs out the current user by clearing the session.")
    @ApiResponse(responseCode = "200", description = "Logout successful")
    @PostMapping(path = ["/auth/logout"])
    fun logout() {
    }
}