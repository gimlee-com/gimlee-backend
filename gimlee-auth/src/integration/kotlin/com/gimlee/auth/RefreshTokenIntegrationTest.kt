package com.gimlee.auth

import com.gimlee.auth.domain.RefreshToken
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.RefreshTokenRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import com.gimlee.auth.web.dto.request.LoginRequestDto
import com.gimlee.auth.web.dto.request.RefreshTokenRequestDto
import com.gimlee.auth.web.dto.request.RevokeSessionRequestDto
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.bson.types.ObjectId
import java.time.Instant
import java.time.temporal.ChronoUnit

class RefreshTokenIntegrationTest(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) : BaseIntegrationTest({

    val userId = ObjectId.get()
    val password = "TestPassword123!"
    val username = "rt_testuser"
    val deviceId = "integration-test-device"

    fun authHeaders(accessToken: String) = restClient.createAuthHeader(accessToken)

    fun login(deviceIdOverride: String = deviceId): Map<String, Any?> {
        val response = restClient.post("/auth/login", LoginRequestDto(username, password, deviceIdOverride))
        response.statusCode shouldBe 200
        return response.bodyAs<Map<String, Any?>>()!!
    }

    fun refresh(refreshToken: String, deviceIdOverride: String = deviceId): com.gimlee.common.http.RestResponse {
        return restClient.post("/auth/token/refresh", RefreshTokenRequestDto(refreshToken, deviceIdOverride))
    }

    beforeSpec {
        mongoTemplate.dropCollection("gimlee-users")
        mongoTemplate.dropCollection("gimlee-userRoles")
        mongoTemplate.dropCollection("gimlee-refreshTokens")

        val salt = generateSalt()
        val (hexSalt, hexHash) = createHexSaltAndPasswordHash(password, salt)

        userRepository.save(
            User(
                id = userId,
                username = username,
                displayName = "Test User",
                email = "rt_test@example.com",
                password = hexHash,
                passwordSalt = hexSalt,
                status = UserStatus.ACTIVE
            )
        )
        userRoleRepository.add(userId, Role.USER)
    }

    Given("login endpoint with refresh tokens") {

        When("user logs in with valid credentials") {
            val body = login()

            Then("response contains both access and refresh tokens") {
                body["success"] shouldBe true
                body["status"] shouldBe "SUCCESS"
                (body["accessToken"] as String).shouldNotBeBlank()
                (body["refreshToken"] as String).shouldNotBeBlank()
            }
        }

        When("user logs in with invalid credentials") {
            val response = restClient.post("/auth/login", LoginRequestDto(username, "WrongPassword!", deviceId))

            Then("response does not contain tokens") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "AUTH_INCORRECT_CREDENTIALS"
                body["accessToken"] shouldBe null
                body["refreshToken"] shouldBe null
            }
        }
    }

    Given("refresh token endpoint") {

        When("valid refresh token is used") {
            val loginBody = login()
            val originalRefreshToken = loginBody["refreshToken"] as String

            val response = refresh(originalRefreshToken)

            Then("new access and refresh tokens are issued") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
                body["status"] shouldBe "SUCCESS"

                val newAccessToken = body["accessToken"] as String
                val newRefreshToken = body["refreshToken"] as String

                newAccessToken.shouldNotBeBlank()
                newRefreshToken.shouldNotBeBlank()
                newRefreshToken shouldNotBe originalRefreshToken
            }
        }

        When("rotated (old) refresh token is reused") {
            val loginBody = login()
            val firstRefreshToken = loginBody["refreshToken"] as String

            // First rotation — succeeds
            val rotateResponse = refresh(firstRefreshToken)
            rotateResponse.statusCode shouldBe 200
            val rotateBody = rotateResponse.bodyAs<Map<String, Any?>>()!!
            val secondRefreshToken = rotateBody["refreshToken"] as String

            // Attempt to reuse the first (now-rotated) token
            val reuseResponse = refresh(firstRefreshToken)

            Then("reuse detection triggers and returns 401") {
                reuseResponse.statusCode shouldBe 401
                val body = reuseResponse.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_REUSE_DETECTED"
            }

            Then("entire family is revoked — rotated token is also invalidated") {
                val response = refresh(secondRefreshToken)
                response.statusCode shouldBe 401
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_REUSE_DETECTED"
            }
        }

        When("invalid/garbage refresh token is provided") {
            val response = refresh("this-is-not-a-valid-token")

            Then("returns 401 with INVALID status") {
                response.statusCode shouldBe 401
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_INVALID"
            }
        }

        When("expired refresh token is used") {
            val expiredToken = "expired-test-token-value"
            val hashedToken = hashToken(expiredToken)
            val expiredRefreshToken = RefreshToken(
                id = UUIDv7.generate(),
                userId = userId.toHexString(),
                familyId = UUIDv7.generate(),
                hashedToken = hashedToken,
                deviceId = deviceId,
                issuedAt = Instant.now().minus(60, ChronoUnit.DAYS).toMicros(),
                expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).toMicros()
            )
            refreshTokenRepository.save(expiredRefreshToken)

            val response = refresh(expiredToken)

            Then("returns 401 with EXPIRED status") {
                response.statusCode shouldBe 401
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_EXPIRED"
            }
        }

        When("new access token from refresh is used on a secured endpoint") {
            val loginBody = login()
            val refreshToken = loginBody["refreshToken"] as String

            val refreshResponse = refresh(refreshToken)
            refreshResponse.statusCode shouldBe 200
            val refreshBody = refreshResponse.bodyAs<Map<String, Any?>>()!!
            val newAccessToken = refreshBody["accessToken"] as String

            val securedResponse = restClient.get("/auth/session/init", authHeaders(newAccessToken))

            Then("secured endpoint accepts the new access token") {
                securedResponse.statusCode shouldBe 200
            }
        }
    }

    Given("session revocation endpoints") {

        When("user revokes a specific session") {
            val loginBody = login()
            val refreshToken = loginBody["refreshToken"] as String
            val accessToken = loginBody["accessToken"] as String

            val revokeResponse = restClient.post(
                "/auth/sessions/revoke",
                RevokeSessionRequestDto(refreshToken),
                authHeaders(accessToken)
            )

            Then("revocation succeeds") {
                revokeResponse.statusCode shouldBe 200
                val body = revokeResponse.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
                body["status"] shouldBe "SUCCESS"
            }

            Then("revoked refresh token can no longer be used") {
                val refreshResponse = refresh(refreshToken)
                refreshResponse.statusCode shouldBe 401
                val body = refreshResponse.bodyAs<Map<String, Any?>>()!!
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_REUSE_DETECTED"
            }
        }

        When("user revokes all sessions") {
            val login1 = login("device-1")
            val login2 = login("device-2")
            val login3 = login("device-3")

            val accessToken = login1["accessToken"] as String

            val revokeAllResponse = restClient.post(
                "/auth/sessions/revoke-all",
                null,
                authHeaders(accessToken)
            )

            Then("revocation succeeds") {
                revokeAllResponse.statusCode shouldBe 200
                val body = revokeAllResponse.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
            }

            Then("all refresh tokens are invalidated") {
                val r1 = refresh(login1["refreshToken"] as String, "device-1")
                val r2 = refresh(login2["refreshToken"] as String, "device-2")
                val r3 = refresh(login3["refreshToken"] as String, "device-3")

                r1.statusCode shouldBe 401
                r2.statusCode shouldBe 401
                r3.statusCode shouldBe 401
            }
        }
    }

    Given("logout endpoint with refresh token revocation") {

        When("user logs out with refresh token in body") {
            val loginBody = login()
            val refreshToken = loginBody["refreshToken"] as String
            val accessToken = loginBody["accessToken"] as String

            val logoutResponse = restClient.post(
                "/auth/logout",
                RevokeSessionRequestDto(refreshToken),
                authHeaders(accessToken)
            )

            Then("logout succeeds") {
                logoutResponse.statusCode shouldBe 200
                val body = logoutResponse.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
            }

            Then("refresh token is no longer valid after logout") {
                val refreshResponse = refresh(refreshToken)
                refreshResponse.statusCode shouldBe 401
            }
        }

        When("user logs out without refresh token in body") {
            val loginBody = login()
            val accessToken = loginBody["accessToken"] as String

            val logoutResponse = restClient.post(
                "/auth/logout",
                null,
                authHeaders(accessToken)
            )

            Then("logout still succeeds gracefully") {
                logoutResponse.statusCode shouldBe 200
                val body = logoutResponse.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
            }
        }
    }

    Given("device-based session independence") {

        When("user logs in from different devices") {
            val login1 = login("phone-ios")
            val login2 = login("browser-chrome")

            val refreshToken1 = login1["refreshToken"] as String
            val refreshToken2 = login2["refreshToken"] as String
            val accessToken1 = login1["accessToken"] as String

            // Revoke only the phone session
            restClient.post(
                "/auth/sessions/revoke",
                RevokeSessionRequestDto(refreshToken1),
                authHeaders(accessToken1)
            )

            Then("revoking one device does not affect the other") {
                val r1 = refresh(refreshToken1, "phone-ios")
                r1.statusCode shouldBe 401

                val r2 = refresh(refreshToken2, "browser-chrome")
                r2.statusCode shouldBe 200
            }
        }
    }

    Given("device binding enforcement") {

        When("refresh token is used from a different device") {
            val loginBody = login("original-device")
            val refreshToken = loginBody["refreshToken"] as String

            val response = refresh(refreshToken, "different-device")

            Then("returns 401 with DEVICE_MISMATCH status") {
                response.statusCode shouldBe 401
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe false
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_DEVICE_MISMATCH"
            }
        }

        When("refresh token is used from the correct device") {
            val loginBody = login("my-device")
            val refreshToken = loginBody["refreshToken"] as String

            val response = refresh(refreshToken, "my-device")

            Then("refresh succeeds") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["success"] shouldBe true
            }
        }

        When("device binding persists after rotation") {
            val loginBody = login("bound-device")
            val refreshToken = loginBody["refreshToken"] as String

            // First rotation from correct device
            val firstRotation = refresh(refreshToken, "bound-device")
            firstRotation.statusCode shouldBe 200
            val newToken = (firstRotation.bodyAs<Map<String, Any?>>()!!)["refreshToken"] as String

            // Attempt second rotation from wrong device
            val response = refresh(newToken, "attacker-device")

            Then("rotated token is also bound to original device") {
                response.statusCode shouldBe 401
                val body = response.bodyAs<Map<String, Any?>>()!!
                body["status"] shouldBe "AUTH_REFRESH_TOKEN_DEVICE_MISMATCH"
            }
        }
    }

    Given("session limit enforcement") {

        When("user exceeds max sessions") {
            // Clear existing tokens for clean test
            mongoTemplate.dropCollection("gimlee-refreshTokens")

            // Login with 12 different devices (limit is 10)
            val sessions = (1..12).map { i ->
                login("device-$i")
            }

            Then("only the 10 most recent sessions remain active") {
                // The oldest 2 sessions should have been revoked
                val oldestRefresh = sessions[0]["refreshToken"] as String
                val oldestResponse = refresh(oldestRefresh, "device-1")
                oldestResponse.statusCode shouldBe 401

                // The newest session should still work
                val newestRefresh = sessions[11]["refreshToken"] as String
                val newestResponse = refresh(newestRefresh, "device-12")
                newestResponse.statusCode shouldBe 200
            }
        }
    }
}) {
    companion object {
        fun hashToken(token: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(token.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
