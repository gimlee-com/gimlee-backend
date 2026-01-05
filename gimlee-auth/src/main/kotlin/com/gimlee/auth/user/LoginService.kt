package com.gimlee.auth.user

import org.apache.commons.codec.binary.Hex
import org.springframework.stereotype.Service
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.auth.util.createHexPasswordHash
import com.gimlee.auth.domain.AuthOutcome
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.domain.model.CommonOutcome
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import java.time.LocalDateTime

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val jwtTokenService: JwtTokenService,
    private val messageSource: MessageSource
) {
    fun login(username: String, plaintextPassword: String): IdentityVerificationResponse {
        val user = userRepository.findOneByField(FIELD_USERNAME, username, includeCredentials = true)

        return if (user == null || user.status == UserStatus.SUSPENDED) {
            val outcome = AuthOutcome.INCORRECT_CREDENTIALS
            IdentityVerificationResponse(
                success = false,
                status = outcome.code,
                message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
            )
        } else if (isPasswordCorrect(plaintextPassword, user)) {
            val accessToken = jwtTokenService.generateToken(
                user.id.toString(),
                user.username!!,
                userRoleRepository.getAll(user.id!!),
                longLived = true
            )
            userRepository.save(user.copy(lastLogin = LocalDateTime.now()))
            val outcome = CommonOutcome.SUCCESS
            IdentityVerificationResponse(
                success = true,
                status = outcome.code,
                message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale()),
                accessToken = accessToken
            )
        } else {
            val outcome = AuthOutcome.INCORRECT_CREDENTIALS
            IdentityVerificationResponse(
                success = false,
                status = outcome.code,
                message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
            )
        }
    }

    private fun isPasswordCorrect(plaintextPassword: String, user: User): Boolean {
        return createHexPasswordHash(
            plaintextPassword,
            Hex.decodeHex(user.passwordSalt)
        ) == user.password
    }
}