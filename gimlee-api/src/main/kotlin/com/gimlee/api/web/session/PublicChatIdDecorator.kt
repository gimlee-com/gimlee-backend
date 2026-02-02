package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Decorator that adds the hardcoded public chat ID to the session initialization response.
 * This is used during the development phase to expose a public chat for all users.
 */
@Component
class PublicChatIdDecorator : SessionDecorator {
    override val name: String = "publicChatId"

    override fun decorate(response: InitSessionResponseDto, request: HttpServletRequest) {
        response.data["publicChatId"] = "019c2016-1e0a-781d-bc00-c002ac9f350f"
    }
}
