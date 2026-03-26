package com.gimlee.auth.interceptor

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.auth.annotation.AllowUserStatus
import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.domain.AuthOutcome
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.model.Principal
import com.gimlee.auth.model.isEmptyOrNull
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.web.dto.StatusResponseDto
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.logging.log4j.LogManager
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

class BannedUserInterceptor(
    private val bannedUserCache: BannedUserCache,
    private val messageSource: MessageSource,
    private val objectMapper: ObjectMapper
) : HandlerInterceptor {

    companion object {
        private val log = LogManager.getLogger()
        private val READ_ONLY_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val principal = HttpServletRequestAuthUtil.getPrincipalOrNull()
        if (principal.isEmptyOrNull()) return true

        if (request.method in READ_ONLY_METHODS) return true

        if (!bannedUserCache.isBanned(principal!!.userId)) return true

        if (handler is HandlerMethod && isEndpointAllowedForBanned(handler)) return true

        log.info("Blocked mutating request from banned user {} to {} {}", principal.userId, request.method, request.requestURI)
        writeBannedResponse(response)
        return false
    }

    private fun isEndpointAllowedForBanned(handler: HandlerMethod): Boolean {
        val annotation = handler.getMethodAnnotation(AllowUserStatus::class.java) ?: return false
        return UserStatus.BANNED in annotation.statuses
    }

    private fun writeBannedResponse(response: HttpServletResponse) {
        val outcome = AuthOutcome.USER_BANNED
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        val body = StatusResponseDto.fromOutcome(outcome, message)

        response.status = outcome.httpCode
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.outputStream, body)
    }
}
