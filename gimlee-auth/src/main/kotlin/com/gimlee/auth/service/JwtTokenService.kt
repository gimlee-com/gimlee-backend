package com.gimlee.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.io.UnsupportedEncodingException
import java.util.Date
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import com.gimlee.auth.model.Role

const val EXPIRES_MINUTES = 30
const val EXPIRES_MILLIS = EXPIRES_MINUTES * 1000 * 60
const val LONG_LIVED_EXPIRES_DAYS = 14
const val LONG_LIVED_EXPIRES_MILLIS = LONG_LIVED_EXPIRES_DAYS * 1000 * 60 * 60 * 24

@Service
class JwtTokenService(
    @Value("\${gimlee.auth.rest.jwt.issuer:q}")
    private val issuer: String,

    @Value("\${gimlee.auth.rest.jwt.key:}")
    private val jwtKey: String
) {

    companion object {
        private const val ROLES_CLAIM = "roles"
        private const val USERNAME_CLAIM = "username"
    }

    fun generateToken(subject: String, username: String, roles: List<Role>, longLived: Boolean): String {
        val expires = if (longLived) LONG_LIVED_EXPIRES_MILLIS else EXPIRES_MILLIS

        try {
            return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date())
                .withExpiresAt(Date(System.currentTimeMillis() + expires))
                .withSubject(subject)
                .withArrayClaim(ROLES_CLAIM, roles.map { it.toString() }.toTypedArray())
                .withClaim(USERNAME_CLAIM, username)
                .sign(Algorithm.HMAC256(jwtKey))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException("Failed to generate JWT token.", e)
        }
    }
}
