package com.gimlee.auth.util

import jakarta.servlet.http.HttpServletRequest

fun extractToken(request: HttpServletRequest): String {
    val authHeader = request.getHeader("Authorization")
    if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
        return authHeader.substring(7).trim()
    }
    return ""
}
