package com.gimlee.support.report.domain

import com.gimlee.common.UUIDv7
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.events.ReportAssignedEvent
import com.gimlee.events.ReportResolvedEvent
import com.gimlee.support.report.domain.model.*
import com.gimlee.support.report.persistence.ReportRepository
import com.gimlee.support.report.persistence.model.ReportDocument
import com.gimlee.support.report.web.dto.response.ReportStatsDto
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class ReportAdminService(
    private val reportRepository: ReportRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val validTransitions = mapOf(
        ReportStatus.OPEN to setOf(ReportStatus.IN_REVIEW, ReportStatus.RESOLVED, ReportStatus.DISMISSED),
        ReportStatus.IN_REVIEW to setOf(ReportStatus.OPEN, ReportStatus.RESOLVED, ReportStatus.DISMISSED)
    )

    fun getReport(reportId: String): Pair<Outcome, Report?> {
        val doc = reportRepository.findById(ObjectId(reportId))
            ?: return Pair(ReportOutcome.REPORT_NOT_FOUND, null)
        return Pair(CommonOutcome.SUCCESS, doc.toDomain())
    }

    fun assignReport(reportId: String, assigneeId: String, performedBy: String): ReportOutcome {
        val doc = reportRepository.findById(ObjectId(reportId))
            ?: return ReportOutcome.REPORT_NOT_FOUND

        val currentStatus = ReportStatus.fromShortName(doc.status)
        if (currentStatus == ReportStatus.RESOLVED || currentStatus == ReportStatus.DISMISSED) {
            return ReportOutcome.REPORT_ALREADY_RESOLVED
        }

        val now = Instant.now().toMicros()
        reportRepository.updateAssignee(doc.id!!, ObjectId(assigneeId), now)

        if (currentStatus == ReportStatus.OPEN) {
            reportRepository.updateStatus(doc.id, ReportStatus.IN_REVIEW, now)
        }

        val entry = ReportDocument.timelineEntryToDocument(ReportTimelineEntry(
            id = UUIDv7.generate().toString(),
            action = ReportTimelineAction.ASSIGNED,
            performedBy = performedBy,
            detail = assigneeId,
            createdAt = now
        ))
        reportRepository.addTimelineEntry(doc.id, entry, now)

        eventPublisher.publishEvent(ReportAssignedEvent(
            reportId = doc.id.toHexString(),
            assigneeId = assigneeId,
            assignedBy = performedBy
        ))

        return ReportOutcome.REPORT_ASSIGNED
    }

    fun updateStatus(reportId: String, newStatus: ReportStatus, performedBy: String): ReportOutcome {
        val doc = reportRepository.findById(ObjectId(reportId))
            ?: return ReportOutcome.REPORT_NOT_FOUND

        val currentStatus = ReportStatus.fromShortName(doc.status)
        val allowed = validTransitions[currentStatus] ?: emptySet()
        if (newStatus !in allowed) {
            return ReportOutcome.REPORT_INVALID_STATUS_TRANSITION
        }

        val now = Instant.now().toMicros()
        reportRepository.updateStatus(doc.id!!, newStatus, now)

        val entry = ReportDocument.timelineEntryToDocument(ReportTimelineEntry(
            id = UUIDv7.generate().toString(),
            action = ReportTimelineAction.STATUS_CHANGED,
            performedBy = performedBy,
            detail = "${currentStatus.name} → ${newStatus.name}",
            createdAt = now
        ))
        reportRepository.addTimelineEntry(doc.id, entry, now)

        return ReportOutcome.REPORT_STATUS_UPDATED
    }

    fun resolveReport(
        reportId: String,
        resolution: ReportResolution,
        internalNotes: String?,
        performedBy: String
    ): ReportOutcome {
        val doc = reportRepository.findById(ObjectId(reportId))
            ?: return ReportOutcome.REPORT_NOT_FOUND

        val currentStatus = ReportStatus.fromShortName(doc.status)
        if (currentStatus == ReportStatus.RESOLVED || currentStatus == ReportStatus.DISMISSED) {
            return ReportOutcome.REPORT_ALREADY_RESOLVED
        }

        val finalStatus = if (resolution.isDismissal) ReportStatus.DISMISSED else ReportStatus.RESOLVED
        val now = Instant.now().toMicros()

        reportRepository.resolve(
            doc.id!!,
            resolution,
            ObjectId(performedBy),
            now,
            internalNotes,
            finalStatus
        )

        val entry = ReportDocument.timelineEntryToDocument(ReportTimelineEntry(
            id = UUIDv7.generate().toString(),
            action = ReportTimelineAction.RESOLVED,
            performedBy = performedBy,
            detail = "${finalStatus.name}: ${resolution.name}",
            createdAt = now
        ))
        reportRepository.addTimelineEntry(doc.id, entry, now)

        eventPublisher.publishEvent(ReportResolvedEvent(
            reportId = doc.id.toHexString(),
            reporterId = doc.reporterId.toHexString(),
            targetId = doc.targetId.toHexString(),
            targetType = doc.targetType,
            resolution = resolution.name,
            resolvedBy = performedBy
        ))

        return ReportOutcome.REPORT_RESOLVED
    }

    fun addNote(reportId: String, note: String, performedBy: String): ReportOutcome {
        val doc = reportRepository.findById(ObjectId(reportId))
            ?: return ReportOutcome.REPORT_NOT_FOUND

        val now = Instant.now().toMicros()
        val entry = ReportDocument.timelineEntryToDocument(ReportTimelineEntry(
            id = UUIDv7.generate().toString(),
            action = ReportTimelineAction.NOTE_ADDED,
            performedBy = performedBy,
            detail = note,
            createdAt = now
        ))
        reportRepository.addTimelineEntry(doc.id!!, entry, now)

        return ReportOutcome.REPORT_NOTE_ADDED
    }

    fun getStats(): ReportStatsDto {
        val todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC).toMicros()
        return ReportStatsDto(
            open = reportRepository.countByStatus(ReportStatus.OPEN),
            inReview = reportRepository.countByStatus(ReportStatus.IN_REVIEW),
            resolvedToday = reportRepository.countResolvedSince(todayStart),
            totalUnresolved = reportRepository.countByStatusIn(listOf(ReportStatus.OPEN, ReportStatus.IN_REVIEW))
        )
    }
}
