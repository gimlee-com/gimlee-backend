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
import com.gimlee.common.domain.model.StatusCode
import com.gimlee.common.web.dto.StatusResponseDto

@ControllerAdvice(basePackages = ["com.gimlee"])
class WebExceptionHandler : ResponseEntityExceptionHandler() {

    companion object {
        private val log = LogManager.getLogger()
    }

    @ExceptionHandler(AuthorizationException::class)
    fun handleAuthorizationException(ex: RuntimeException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Unauthorized access", ex)
        return handleExceptionInternal(
            ex,
            StatusResponseDto.fromStatusCode(StatusCode.UNAUTHORIZED),
            HttpHeaders(),
            HttpStatus.FORBIDDEN,
            req
        )
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: RuntimeException, req: WebRequest): ResponseEntity<Any>? {
        log.warn("Unauthenticated access", ex)
        return handleExceptionInternal(
            ex,
            StatusResponseDto.fromStatusCode(StatusCode.MISSING_CREDENTIALS),
            HttpHeaders(),
            HttpStatus.UNAUTHORIZED,
            req
        )
    }
}
