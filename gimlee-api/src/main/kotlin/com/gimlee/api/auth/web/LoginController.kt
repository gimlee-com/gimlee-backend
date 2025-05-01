package com.gimlee.api.auth.web

import jakarta.servlet.ServletContext
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.gimlee.api.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.api.auth.service.user.LoginService
import com.gimlee.api.auth.web.dto.request.LoginRequestDto

@RestController
class LoginController(
    private val loginService: LoginService,
    private val servletContext: ServletContext
) {
    @PostMapping(path = ["/auth/login"])
    fun login(
        @RequestBody loginData: LoginRequestDto,
        response: HttpServletResponse
    ): IdentityVerificationResponse {
        val loginResponse = loginService.login(loginData.username, loginData.password)
        response.addCookie(createAccessTokenCookie(loginResponse))

        return loginResponse
    }

    private fun createAccessTokenCookie(loginResponse: IdentityVerificationResponse): Cookie {
        val accessTokenCookie = Cookie("JWT", loginResponse.accessToken)
        accessTokenCookie.path = "${servletContext.contextPath}/"
        accessTokenCookie.isHttpOnly = true
        return accessTokenCookie
    }
}