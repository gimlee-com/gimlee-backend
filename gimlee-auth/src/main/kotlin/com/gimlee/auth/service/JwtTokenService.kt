package com.gimlee.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import jakarta.annotation.PostConstruct
import java.io.UnsupportedEncodingException
import java.util.Date
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import com.gimlee.auth.model.Role

@Service
class JwtTokenService(
    @Value("\${gimlee.auth.rest.jwt.issuer:q}")
    private val issuer: String,

    @Value("\${gimlee.auth.rest.jwt.key:}")
    private val jwtKey: String,

    @Value("\${gimlee.auth.token.access-ttl-minutes:15}")
    private val accessTtlMinutes: Long
) {

    companion object {
        private const val ROLES_CLAIM = "roles"
        private const val USERNAME_CLAIM = "username"
        private const val MIN_KEY_LENGTH = 32
    }

    @PostConstruct
    fun validateConfiguration() {
        require(jwtKey.isNotBlank()) { "JWT signing key (gimlee.auth.rest.jwt.key) must not be blank" }
        require(jwtKey.length >= MIN_KEY_LENGTH) {
            "JWT signing key (gimlee.auth.rest.jwt.key) must be at least $MIN_KEY_LENGTH characters, got ${jwtKey.length}"
        }
    }

    fun generateToken(subject: String, username: String, roles: List<Role>): String {
        val expiresMillis = accessTtlMinutes * 60 * 1000

        try {
            return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date())
                .withExpiresAt(Date(System.currentTimeMillis() + expiresMillis))
                .withSubject(subject)
                .withArrayClaim(ROLES_CLAIM, roles.map { it.toString() }.toTypedArray())
                .withClaim(USERNAME_CLAIM, username)
                .sign(Algorithm.HMAC256(jwtKey))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException("Failed to generate JWT token.", e)
        }
    }
}
