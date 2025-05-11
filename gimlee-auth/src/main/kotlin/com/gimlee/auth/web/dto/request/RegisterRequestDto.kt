package com.gimlee.auth.web.dto.request

import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class RegisterRequestDto(
    @get:Size(min = 1, max = 30)
    val username: String?,
    @get:Email
    val email: String?,
    val phone: String?,
    @get:Size(min = 8, max = 64)
    val password: String
) {
    fun toUser(): User {
        val (salt, passwordHash) = createHexSaltAndPasswordHash(password, generateSalt())

        return User(
            username = username,
            email = email,
            phone = phone,
            password = passwordHash,
            passwordSalt = salt,
            status = UserStatus.PENDING_VERIFICATION
        )
    }
}