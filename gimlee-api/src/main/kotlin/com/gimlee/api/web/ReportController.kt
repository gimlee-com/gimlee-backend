package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.reports.domain.ReportOutcome
import com.gimlee.reports.domain.ReportService
import com.gimlee.reports.web.dto.request.ReportRequestDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Reports", description = "Content reporting endpoints")
@RestController
@RequestMapping("/reports")
class ReportController(
    private val reportService: ReportService,
    private val messageSource: MessageSource
) {

    @Operation(
        summary = "Report Content",
        description = "Submit a report against a piece of content (question, answer, etc.) for violating platform guidelines. One report per user per target."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report submitted. Possible status codes: REPORT_SUBMITTED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Target not found. Possible status codes: REPORT_TARGET_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Already reported. Possible status codes: ALREADY_REPORTED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping
    @Privileged(role = "USER")
    fun submitReport(@Valid @RequestBody request: ReportRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val outcome = reportService.submitReport(request.targetType, request.targetId, principal.userId, request.reason)
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message))
    }
}
