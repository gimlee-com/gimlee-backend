package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.support.report.domain.ReportService
import com.gimlee.support.report.web.dto.request.ReportRequestDto
import com.gimlee.support.report.web.dto.response.ReportListItemDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Reports", description = "Content reporting endpoints")
@RestController
@RequestMapping("/reports")
class ReportController(
    private val reportService: ReportService,
    private val userService: UserService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Report Content",
        description = "Submit a report against a piece of content (ad, question, answer, message, or user) for violating platform guidelines. " +
            "One report per user per target."
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
        val outcome = reportService.submitReport(request.targetType, request.targetId, principal.userId, request.reason, request.description)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "My reports",
        description = "Fetches a paginated list of reports submitted by the authenticated user."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of user's reports")
    @GetMapping("/mine")
    @Privileged(role = "USER")
    fun myReports(
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): Page<ReportListItemDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val reportsPage = reportService.getReporterReports(principal.userId, page, size)

        val userIds = mutableSetOf<String>()
        reportsPage.content.forEach { report ->
            userIds.add(report.reporterId)
            report.assigneeId?.let { userIds.add(it) }
        }
        val usernames = userService.findUsernamesByIds(userIds.toList())

        return reportsPage.map { report ->
            ReportListItemDto(
                id = report.id,
                targetType = report.targetType,
                targetId = report.targetId,
                targetTitle = report.targetTitle,
                reason = report.reason,
                status = report.status,
                reporterUsername = usernames[report.reporterId],
                reporterUserId = report.reporterId,
                assigneeUsername = report.assigneeId?.let { usernames[it] },
                assigneeUserId = report.assigneeId,
                createdAt = report.createdAt,
                updatedAt = report.updatedAt,
                siblingCount = report.siblingCount,
                description = report.description
            )
        }
    }
}
