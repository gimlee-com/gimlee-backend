package com.gimlee.api.web.session

import com.gimlee.api.web.dto.InitSessionResponseDto
import jakarta.servlet.http.HttpServletRequest

interface SessionDecorator {
    val name: String
    fun decorate(response: InitSessionResponseDto, request: HttpServletRequest)
}
