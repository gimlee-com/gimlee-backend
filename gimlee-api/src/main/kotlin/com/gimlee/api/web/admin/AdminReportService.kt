package com.gimlee.api.web.admin

import com.gimlee.auth.service.UserService
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.support.report.domain.ReportAdminService
import com.gimlee.support.report.domain.model.ReportReason
import com.gimlee.support.report.domain.model.ReportStatus
import com.gimlee.support.report.domain.model.ReportTargetType
import com.gimlee.support.report.persistence.ReportRepository
import com.gimlee.support.report.web.dto.response.ReportDetailDto
import com.gimlee.support.report.web.dto.response.ReportListItemDto
import com.gimlee.support.report.web.dto.response.ReportTimelineEntryDto
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AdminReportService(
    private val reportAdminService: ReportAdminService,
    private val reportRepository: ReportRepository,
    private val userService: UserService
) {

    fun listReports(
        status: ReportStatus?, targetType: ReportTargetType?,
        reason: ReportReason?, assigneeId: String?,
        search: String?, sort: String?, direction: String?,
        page: Int, size: Int
    ): Page<ReportListItemDto> {
        val pageable = PageRequest.of(page, size)
        val assigneeObjectId = assigneeId?.let { ObjectId(it) }
        val reportsPage = reportRepository.findAllPaginated(
            status, targetType, reason, assigneeObjectId, search, sort, direction, pageable
        )

        val userIds = collectReportUserIds(reportsPage.content)
        val usernames = userService.findUsernamesByIds(userIds)

        return reportsPage.map { doc ->
            val report = doc.toDomain()
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

    fun getReportDetail(reportId: String): Pair<Outcome, ReportDetailDto?> {
        val (outcome, report) = reportAdminService.getReport(reportId)
        if (report == null) return Pair(outcome, null)

        val userIds = mutableSetOf(report.reporterId)
        report.assigneeId?.let { userIds.add(it) }
        report.resolvedBy?.let { userIds.add(it) }
        report.timeline.forEach { userIds.add(it.performedBy) }
        val usernames = userService.findUsernamesByIds(userIds.toList())

        val dto = ReportDetailDto(
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
            description = report.description,
            targetSnapshot = report.targetSnapshot,
            resolution = report.resolution,
            resolvedByUsername = report.resolvedBy?.let { usernames[it] },
            resolvedAt = report.resolvedAt,
            internalNotes = report.internalNotes,
            timeline = report.timeline.map { entry ->
                ReportTimelineEntryDto(
                    id = entry.id,
                    action = entry.action,
                    performedByUsername = usernames[entry.performedBy],
                    detail = entry.detail,
                    createdAt = entry.createdAt
                )
            }
        )

        return Pair(outcome, dto)
    }

    fun listSiblingReports(
        targetType: ReportTargetType, targetId: String,
        excludeReportId: String?, page: Int, size: Int
    ): Page<ReportListItemDto> {
        val pageable = PageRequest.of(page, size)
        val excludeId = excludeReportId?.let { ObjectId(it) }
        val reportsPage = reportRepository.findByTargetPaginated(
            targetType, ObjectId(targetId), excludeId, pageable
        )

        val userIds = collectReportUserIds(reportsPage.content)
        val usernames = userService.findUsernamesByIds(userIds)

        return reportsPage.map { doc ->
            val report = doc.toDomain()
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

    private fun collectReportUserIds(docs: List<com.gimlee.support.report.persistence.model.ReportDocument>): List<String> {
        val userIds = mutableSetOf<String>()
        docs.forEach { doc ->
            userIds.add(doc.reporterId.toHexString())
            doc.assigneeId?.let { userIds.add(it.toHexString()) }
        }
        return userIds.toList()
    }
}
