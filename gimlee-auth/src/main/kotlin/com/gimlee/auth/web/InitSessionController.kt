package com.gimlee.auth.web

import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.exception.AuthenticationException
import com.gimlee.auth.util.getJwtCookie
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class InitSessionController {
    @GetMapping(path = ["/auth/session/init"])
    fun init(
        request: HttpServletRequest
    ): IdentityVerificationResponse {
        return try {
            IdentityVerificationResponse(success = true, accessToken = getJwtCookie(request).value)
        } catch (e: AuthenticationException) {
            IdentityVerificationResponse(success = true, accessToken = null)
        }

    }
}