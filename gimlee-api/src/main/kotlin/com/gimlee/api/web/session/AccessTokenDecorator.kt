package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import com.gimlee.auth.util.extractToken
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class AccessTokenDecorator : SessionDecorator {
    override val name: String = "accessToken"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        try {
            response.data["accessToken"] = extractToken(request)
        } catch (e: Exception) {
            response.data["accessToken"] = null
        }
    }
}
