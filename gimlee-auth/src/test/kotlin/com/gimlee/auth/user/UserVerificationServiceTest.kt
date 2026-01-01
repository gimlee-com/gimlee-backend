package com.gimlee.auth.user

import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.persistence.VerificationCodeRepository
import com.gimlee.auth.service.JwtTokenService
import com.gimlee.notifications.email.EmailService
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.types.ObjectId
import org.springframework.context.MessageSource
import org.springframework.core.io.ResourceLoader
import java.io.ByteArrayInputStream
import java.util.*

class UserVerificationServiceTest : StringSpec({

    val verificationCodeRepository = mockk<VerificationCodeRepository>()
    val userRoleRepository = mockk<UserRoleRepository>()
    val userRepository = mockk<UserRepository>()
    val emailService = mockk<EmailService>()
    val jwtTokenService = mockk<JwtTokenService>()
    val resourceLoader = mockk<ResourceLoader>()
    val messageSource = mockk<MessageSource>()

    val mustacheContent = """
        Title: {{title}}
        Summary: {{summary}}
        Content: {{&content}}
        Code: {{verificationCodePart1}} {{verificationCodePart2}}
        SentBy: {{sentBy}}
        IgnoreNote: {{ignoreNote}}
    """.trimIndent()

    val resource = mockk<org.springframework.core.io.Resource>()
    every { resource.inputStream } returns ByteArrayInputStream(mustacheContent.toByteArray())
    every { resourceLoader.getResource("classpath:email/verification.mustache") } returns resource

    val service = UserVerificationService(
        verificationCodeRepository,
        userRoleRepository,
        userRepository,
        emailService,
        jwtTokenService,
        resourceLoader,
        messageSource
    )

    "should send verification email with username in summary" {
        // given
        val user = User(
            id = ObjectId(),
            username = "testuser",
            email = "test@example.com"
        )
        val locale = Locale.ENGLISH

        every { messageSource.getMessage("gimlee.user.verification.email.title", null, any()) } returns "Verification Title"
        every { messageSource.getMessage("gimlee.user.verification.email.summary", arrayOf("testuser"), any()) } returns "Welcome aboard, testuser!"
        every { messageSource.getMessage("gimlee.user.verification.email.content", null, any()) } returns "Verification Content"
        every { messageSource.getMessage("gimlee.user.verification.email.footer.sent-by", null, any()) } returns "Sent by Gimlee"
        every { messageSource.getMessage("gimlee.user.verification.email.footer.ignore-note", null, any()) } returns "Ignore if not you"
        
        every { verificationCodeRepository.add(any(), any()) } returns mockk()
        every { emailService.sendEmail(any(), any(), any()) } returns Unit

        // when
        service.sendVerificationCode(user)

        // then
        val contentSlot = slot<String>()
        verify {
            emailService.sendEmail(
                to = "test@example.com",
                title = "Verification Title",
                content = capture(contentSlot)
            )
        }

        val capturedContent = contentSlot.captured
        capturedContent shouldContain "Summary: Welcome aboard, testuser!"
    }
})

private infix fun String.shouldContain(substring: String) {
    if (!this.contains(substring)) {
        throw AssertionError("String did not contain expected substring.\nExpected substring: $substring\nActual string: $this")
    }
}
