package com.gimlee.ratings

import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.domain.AuthOutcome
import com.gimlee.auth.exception.AuthenticationException
import com.gimlee.auth.exception.AuthorizationException
import com.gimlee.auth.service.UserService
import com.gimlee.auth.user.UserVerificationService
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.FieldErrorDto
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.notifications.email.EmailService
import io.mockk.mockk
import org.apache.logging.log4j.LogManager
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@TestConfiguration
class RatingTestConfig {
    @Bean
    fun emailService(): EmailService = mockk(relaxed = true)

    @Bean
    @Primary
    fun userService(): UserService = mockk(relaxed = true)

    @Bean
    @Primary
    fun userVerificationService(): UserVerificationService = mockk(relaxed = true)

    @Bean
    @Primary
    fun bannedUserCache(): BannedUserCache = mockk(relaxed = true)
}

@Configuration
class TestWebExceptionHandler(private val messageSource: MessageSource) : ResponseEntityExceptionHandler() {

    companion object {
        private val log = LogManager.getLogger()
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage(CommonOutcome.BAD_REQUEST.messageKey, null, locale)
        return handleExceptionInternal(
            ex,
            StatusResponseDto.fromOutcome(CommonOutcome.BAD_REQUEST, message),
            headers,
            HttpStatus.BAD_REQUEST,
            request
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val locale = LocaleContextHolder.getLocale()
        val fieldErrors = ex.bindingResult.fieldErrors.map { FieldErrorDto(it.field, it.defaultMessage ?: "Invalid value.") }
        val message = messageSource.getMessage(CommonOutcome.BAD_REQUEST.messageKey, null, locale)
        return handleExceptionInternal(
            ex,
            StatusResponseDto.fromOutcome(CommonOutcome.BAD_REQUEST, message, fieldErrors = fieldErrors),
            headers,
            HttpStatus.BAD_REQUEST,
            request
        )
    }

    @ExceptionHandler(AuthorizationException::class)
    fun handleAuthorizationException(ex: AuthorizationException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Unauthorized access to resource: ${ex.resource}", ex)
        return handleOutcome(CommonOutcome.UNAUTHORIZED, req)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: RuntimeException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Unauthenticated access", ex)
        return handleOutcome(AuthOutcome.MISSING_CREDENTIALS, req)
    }

    private fun handleOutcome(outcome: Outcome, req: WebRequest): ResponseEntity<Any>? {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return handleExceptionInternal(
            Exception(message),
            StatusResponseDto.fromOutcome(outcome, message),
            HttpHeaders(),
            HttpStatus.valueOf(outcome.httpCode),
            req
        )
    }
}
