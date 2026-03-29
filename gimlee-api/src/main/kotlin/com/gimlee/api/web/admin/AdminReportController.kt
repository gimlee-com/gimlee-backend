package com.gimlee.api.web.admin

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.support.report.domain.ReportAdminService
import com.gimlee.support.report.domain.ReportOutcome
import com.gimlee.support.report.domain.model.ReportReason
import com.gimlee.support.report.domain.model.ReportResolution
import com.gimlee.support.report.domain.model.ReportStatus
import com.gimlee.support.report.domain.model.ReportTargetType
import com.gimlee.support.report.web.dto.request.AddReportNoteRequestDto
import com.gimlee.support.report.web.dto.request.AssignReportRequestDto
import com.gimlee.support.report.web.dto.request.ResolveReportRequestDto
import com.gimlee.support.report.web.dto.request.UpdateReportStatusRequestDto
import com.gimlee.support.report.web.dto.response.ReportDetailDto
import com.gimlee.support.report.web.dto.response.ReportListItemDto
import com.gimlee.support.report.web.dto.response.ReportStatsDto
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin Reports", description = "Admin endpoints for content report management")
@RestController
@RequestMapping("/admin/reports")
class AdminReportController(
    private val adminReportService: AdminReportService,
    private val reportAdminService: ReportAdminService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List reports",
        description = "Fetches a paginated list of content reports with optional filtering by status, target type, reason, and assignee."
    )
    @ApiResponse(responseCode = "200", description = "Paginated report list")
    @GetMapping
    @Privileged(role = "SUPPORT")
    fun listReports(
        @Parameter(description = "Filter by report status") @RequestParam(required = false) status: ReportStatus?,
        @Parameter(description = "Filter by target type") @RequestParam(required = false) targetType: ReportTargetType?,
        @Parameter(description = "Filter by report reason") @RequestParam(required = false) reason: ReportReason?,
        @Parameter(description = "Filter by assignee user ID") @RequestParam(required = false) assigneeId: String?,
        @Parameter(description = "Search by target title") @RequestParam(required = false) search: String?,
        @Parameter(description = "Sort field (createdAt, updatedAt, siblingCount)") @RequestParam(required = false) sort: String?,
        @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(required = false, defaultValue = "DESC") direction: String?,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "30") size: Int
    ): Page<ReportListItemDto> {
        return adminReportService.listReports(status, targetType, reason, assigneeId, search, sort, direction, page, size)
    }

    @Operation(
        summary = "Get report detail",
        description = "Fetches detailed information about a specific report including timeline, snapshot, and resolution details."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report detail",
        content = [Content(schema = Schema(implementation = ReportDetailDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Report not found",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{reportId}")
    @Privileged(role = "SUPPORT")
    fun getReportDetail(
        @Parameter(description = "Report ID") @PathVariable reportId: String
    ): ResponseEntity<Any> {
        val (outcome, data) = adminReportService.getReportDetail(reportId)
        return handleOutcome(outcome, data)
    }

    @Operation(
        summary = "List sibling reports",
        description = "Fetches other reports for the same target content, useful for seeing the full picture of reports against a single item."
    )
    @ApiResponse(responseCode = "200", description = "Paginated sibling report list")
    @GetMapping("/by-target")
    @Privileged(role = "SUPPORT")
    fun listSiblingReports(
        @Parameter(description = "Target type", required = true) @RequestParam targetType: ReportTargetType,
        @Parameter(description = "Target ID", required = true) @RequestParam targetId: String,
        @Parameter(description = "Report ID to exclude") @RequestParam(required = false) excludeReportId: String?,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "30") size: Int
    ): Page<ReportListItemDto> {
        return adminReportService.listSiblingReports(targetType, targetId, excludeReportId, page, size)
    }

    @Operation(
        summary = "Assign report",
        description = "Assigns a report to a support team member. Automatically transitions open reports to IN_REVIEW."
    )
    @ApiResponse(responseCode = "200", description = "Report assigned", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Report not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "409", description = "Report already resolved", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PatchMapping("/{reportId}/assign")
    @Privileged(role = "SUPPORT")
    fun assignReport(
        @Parameter(description = "Report ID") @PathVariable reportId: String,
        @Valid @RequestBody request: AssignReportRequestDto
    ): ResponseEntity<Any> {
        val performedBy = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = reportAdminService.assignReport(reportId, request.assigneeUserId, performedBy)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Update report status",
        description = "Changes the status of a report. Valid transitions: OPEN → IN_REVIEW/RESOLVED/DISMISSED, IN_REVIEW → OPEN/RESOLVED/DISMISSED."
    )
    @ApiResponse(responseCode = "200", description = "Status updated", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "400", description = "Invalid status transition", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Report not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PatchMapping("/{reportId}/status")
    @Privileged(role = "SUPPORT")
    fun updateReportStatus(
        @Parameter(description = "Report ID") @PathVariable reportId: String,
        @Valid @RequestBody request: UpdateReportStatusRequestDto
    ): ResponseEntity<Any> {
        val performedBy = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = reportAdminService.updateStatus(reportId, request.status, performedBy)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Resolve or dismiss report",
        description = "Resolves a report with a resolution type and optional internal notes. Dismissal resolutions (NO_VIOLATION, DUPLICATE) set status to DISMISSED."
    )
    @ApiResponse(responseCode = "200", description = "Report resolved", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Report not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "409", description = "Report already resolved", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{reportId}/resolve")
    @Privileged(role = "SUPPORT")
    fun resolveReport(
        @Parameter(description = "Report ID") @PathVariable reportId: String,
        @Valid @RequestBody request: ResolveReportRequestDto
    ): ResponseEntity<Any> {
        val performedBy = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = reportAdminService.resolveReport(reportId, request.resolution, request.internalNotes, performedBy)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Add internal note",
        description = "Adds an internal note to a report's timeline. Visible only to support staff."
    )
    @ApiResponse(responseCode = "200", description = "Note added", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Report not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{reportId}/notes")
    @Privileged(role = "SUPPORT")
    fun addNote(
        @Parameter(description = "Report ID") @PathVariable reportId: String,
        @Valid @RequestBody request: AddReportNoteRequestDto
    ): ResponseEntity<Any> {
        val performedBy = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = reportAdminService.addNote(reportId, request.note, performedBy)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Get report statistics",
        description = "Returns dashboard statistics: open reports, in-review, resolved today, and total unresolved."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report statistics",
        content = [Content(schema = Schema(implementation = ReportStatsDto::class))]
    )
    @GetMapping("/stats")
    @Privileged(role = "SUPPORT")
    fun getStats(): ReportStatsDto {
        return reportAdminService.getStats()
    }
}
