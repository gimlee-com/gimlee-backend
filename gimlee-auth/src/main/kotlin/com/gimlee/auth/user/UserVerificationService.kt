package com.gimlee.auth.user

import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.User.Companion.FIELD_ID
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.domain.auth.IdentityVerificationResponse
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.persistence.VerificationCodeRepository
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.notifications.email.EmailService
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import org.apache.commons.lang3.RandomStringUtils
import org.bson.types.ObjectId
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import java.io.StringWriter
import java.time.LocalDateTime

@Service
class UserVerificationService(
    private val verificationCodeRepository: VerificationCodeRepository,
    private val userRoleRepository: UserRoleRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val jwtTokenService: JwtTokenService,
    private val resourceLoader: ResourceLoader,
    private val messageSource: MessageSource
) {
    companion object {
        private const val VERIFICATION_CODE_LENGTH = 6
    }

    private val emailTemplate: Mustache = DefaultMustacheFactory().compile(
        InputStreamReader(
            resourceLoader.getResource("classpath:email/verification.mustache").inputStream,
            Charsets.UTF_8
        ),
        "emailTemplate"
    )

    fun sendVerificationCode(user: User) {
        val verificationCode = RandomStringUtils.randomNumeric(VERIFICATION_CODE_LENGTH)
        val verificationCodeParts = verificationCode.chunked(3)

        val emailData = getEmailData(verificationCodeParts)
        val emailWriter = StringWriter()
        emailTemplate.execute(emailWriter, emailData).flush()

        verificationCodeRepository.add(user.id!!, verificationCode)
        emailService.sendEmail(user.email!!, emailData["title"]!!, emailWriter.toString())
    }

    fun verifyCode(userId: ObjectId, code: String): IdentityVerificationResponse {
        return if (verificationCodeRepository.existsAndIsNotExpired(userId, code)) {
            val user = activateUser(userId)
            IdentityVerificationResponse(
                success = true,
                accessToken = jwtTokenService.generateToken(
                    userId.toHexString(),
                    user.username!!,
                    userRoleRepository.getAll(userId),
                    true
                )
            )
        } else {
            IdentityVerificationResponse.unsuccessful
        }
    }

    private fun activateUser(userId: ObjectId): User {
        userRoleRepository.remove(userId, Role.UNVERIFIED)
        userRoleRepository.add(userId, Role.USER)
        return userRepository.save(
            userRepository
                .findOneByField(FIELD_ID, userId, includeCredentials = true)!!
                .copy(
                    status = UserStatus.ACTIVE,
                    lastLogin = LocalDateTime.now()
                )
        )
    }

    private fun getEmailData(verificationCodeParts: List<String>): HashMap<String, String> {
        val emailData = HashMap<String, String>()
        emailData["verificationCodePart1"] = verificationCodeParts[0]
        emailData["verificationCodePart2"] = verificationCodeParts[1]

        emailData["title"] =
            messageSource.getMessage("gimlee.user.verification.email.title", null, LocaleContextHolder.getLocale())
        emailData["summary"] =
            messageSource.getMessage("gimlee.user.verification.email.summary", null, LocaleContextHolder.getLocale())
        emailData["content"] =
            messageSource.getMessage("gimlee.user.verification.email.content", null, LocaleContextHolder.getLocale())
        emailData["sentBy"] =
            messageSource.getMessage(
                "gimlee.user.verification.email.footer.sent-by",
                null,
                LocaleContextHolder.getLocale()
            )
        emailData["ignoreNote"] = messageSource.getMessage(
            "gimlee.user.verification.email.footer.ignore-note",
            null,
            LocaleContextHolder.getLocale()
        )
        return emailData
    }
}
