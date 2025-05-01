package com.gimlee.auth.util

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest

private val emptyCookie = Cookie("JWT", "")

fun getJwtCookie(request: HttpServletRequest): Cookie {
    val cookies = request.cookies?: emptyArray()
    for (cookie in cookies) {
        if (cookie.name == "JWT") {
            return cookie
        }
    }

    return emptyCookie
}