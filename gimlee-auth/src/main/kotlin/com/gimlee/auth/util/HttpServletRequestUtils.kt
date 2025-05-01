package com.gimlee.auth.util

import java.util.*
import jakarta.servlet.http.HttpServletRequest

fun getClientIpAddress(request: HttpServletRequest): String {
    val xForwardedForHeader = request.getHeader("X-Forwarded-For")
    return if (xForwardedForHeader == null) {
        request.remoteAddr
    } else {
        // As of https://en.wikipedia.org/wiki/X-Forwarded-For
        // The general format of the field is: X-Forwarded-For: client, proxy1, proxy2 ...
        // we only want the client
        StringTokenizer(xForwardedForHeader, ",").nextToken().trim { it <= ' ' }
    }
}