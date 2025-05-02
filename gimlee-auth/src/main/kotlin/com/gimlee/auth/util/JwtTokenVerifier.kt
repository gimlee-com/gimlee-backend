package com.gimlee.auth.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.gimlee.auth.exception.AuthenticationException
import java.io.UnsupportedEncodingException
import jakarta.servlet.http.HttpServletRequest

class JwtTokenVerifier(
    private val jwtKey: String?,
    private val jwtIssuer: String?
) {

    fun verifyToken(request: HttpServletRequest) {
        val jwtCookie = getJwtCookie(request)
        verifyToken(jwtCookie.value)
    }

    fun verifyToken(token: String) {
        try {
            JWT.require(Algorithm.HMAC256(jwtKey!!))
                .withIssuer(jwtIssuer)
                .build()
                .verify(token)
        } catch (e: UnsupportedEncodingException) {
            throw AuthenticationException("Invalid token", e)
        }
    }
}
