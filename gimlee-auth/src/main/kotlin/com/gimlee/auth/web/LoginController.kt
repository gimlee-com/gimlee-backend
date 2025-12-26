package com.gimlee.auth.web

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.user.LoginService
import com.gimlee.auth.web.dto.request.LoginRequestDto

@RestController
class LoginController(
    private val loginService: LoginService
) {
    @PostMapping(path = ["/auth/login"])
    fun login(
        @RequestBody loginData: LoginRequestDto
    ): IdentityVerificationResponse {
        return loginService.login(loginData.username, loginData.password)
    }

    @PostMapping(path = ["/auth/logout"])
    fun logout() {
    }
}