package com.gimlee.auth.util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gimlee.auth.exception.AuthenticationException
import java.io.UnsupportedEncodingException

class JwtTokenVerifier(
    private val jwtKey: String?,
    private val jwtIssuer: String?
) {

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
