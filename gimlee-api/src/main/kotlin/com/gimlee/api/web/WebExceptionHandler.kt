package com.gimlee.api.web

import org.apache.logging.log4j.LogManager
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import com.gimlee.auth.exception.AuthenticationException
import com.gimlee.auth.exception.AuthorizationException
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.AdOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.auth.domain.AuthOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@ControllerAdvice(basePackages = ["com.gimlee"])
class WebExceptionHandler(private val messageSource: MessageSource) : ResponseEntityExceptionHandler() {

    companion object {
        private val log = LogManager.getLogger()
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

    @ExceptionHandler(AdService.AdOperationException::class)
    fun handleAdOperationException(ex: AdService.AdOperationException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Ad operation failed: ${ex.message}")
        val outcome = AdOutcome.INVALID_OPERATION
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return handleExceptionInternal(
            ex,
            StatusResponseDto.fromOutcome(outcome, "$message ${ex.message}"),
            HttpHeaders(),
            HttpStatus.valueOf(outcome.httpCode),
            req
        )
    }

    @ExceptionHandler(AdService.AdCurrencyRoleException::class)
    fun handleAdCurrencyRoleException(ex: AdService.AdCurrencyRoleException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Ad currency role validation failed: ${ex.outcome}")
        return handleOutcome(ex.outcome, req)
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
