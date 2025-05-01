package com.gimlee.api.auth.service.user

import org.apache.commons.codec.binary.Hex
import org.springframework.stereotype.Service
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.auth.util.createHexPasswordHash
import com.gimlee.api.auth.domain.User
import com.gimlee.api.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.api.auth.domain.UserStatus
import com.gimlee.api.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.api.auth.persistence.UserRepository
import com.gimlee.api.auth.persistence.UserRoleRepository
import java.time.LocalDateTime

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val jwtTokenService: JwtTokenService
) {
    fun login(username: String, plaintextPassword: String): IdentityVerificationResponse {
        val user = userRepository.findOneByField(FIELD_USERNAME, username, includeCredentials = true)

        return if (user == null || user.status == UserStatus.SUSPENDED) {
            IdentityVerificationResponse.unsuccessful
        } else if (isPasswordCorrect(plaintextPassword, user)) {
            val accessToken = jwtTokenService.generateToken(
                user.id.toString(),
                user.username!!,
                userRoleRepository.getAll(user.id!!),
                longLived = true
            )
            userRepository.save(user.copy(lastLogin = LocalDateTime.now()))
            IdentityVerificationResponse(
                success = true,
                accessToken = accessToken
            )
        } else {
            IdentityVerificationResponse.unsuccessful
        }
    }

    private fun isPasswordCorrect(plaintextPassword: String, user: User): Boolean {
        return createHexPasswordHash(
            plaintextPassword,
            Hex.decodeHex(user.passwordSalt)
        ) == user.password
    }
}