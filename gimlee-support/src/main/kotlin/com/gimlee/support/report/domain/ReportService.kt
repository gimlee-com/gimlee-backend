package com.gimlee.support.report.domain

import com.gimlee.common.UUIDv7
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.gimlee.common.toMicros
import com.gimlee.events.ReportSubmittedEvent
import com.gimlee.support.report.domain.model.*
import com.gimlee.support.report.persistence.ReportRepository
import com.gimlee.support.report.persistence.model.ReportDocument
import com.mongodb.MongoException
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val resolvers: List<ReportTargetResolver>,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reporterId: String,
        reason: ReportReason,
        description: String?
    ): ReportOutcome {
        if (!reason.supportsTarget(targetType)) {
            log.warn("Reason {} is not applicable for target type {}", reason, targetType)
            return ReportOutcome.REPORT_REASON_NOT_APPLICABLE
        }

        val resolver = resolvers.firstOrNull { it.supports(targetType) }
        if (resolver == null) {
            log.warn("No resolver registered for target type {}", targetType)
            return ReportOutcome.REPORT_TARGET_NOT_FOUND
        }

        val targetInfo = resolver.resolve(targetType, targetId) ?: return ReportOutcome.REPORT_TARGET_NOT_FOUND

        val targetObjectId = ObjectId(targetInfo.targetId)
        val reporterObjectId = ObjectId(reporterId)

        if (reportRepository.existsByTargetAndReporter(targetObjectId, reporterObjectId)) {
            return ReportOutcome.ALREADY_REPORTED
        }

        val now = Instant.now().toMicros()
        val existingCount = reportRepository.countByTarget(targetType.shortName, targetObjectId)
        val newSiblingCount = existingCount + 1

        val timelineEntry = ReportTimelineEntry(
            id = UUIDv7.generate().toString(),
            action = ReportTimelineAction.CREATED,
            performedBy = reporterId,
            detail = null,
            createdAt = now
        )

        val savedDoc: ReportDocument
        try {
            savedDoc = reportRepository.save(
                ReportDocument(
                    targetId = targetObjectId,
                    targetType = targetType.shortName,
                    contextId = targetInfo.contextId?.let { ObjectId(it) },
                    reporterId = reporterObjectId,
                    reason = reason.shortName,
                    description = description,
                    status = ReportStatus.OPEN.shortName,
                    targetTitle = targetInfo.targetTitle,
                    targetSnapshot = Document(targetInfo.snapshot),
                    assigneeId = null,
                    resolution = null,
                    resolvedBy = null,
                    resolvedAt = null,
                    internalNotes = null,
                    siblingCount = newSiblingCount,
                    timeline = listOf(ReportDocument.timelineEntryToDocument(timelineEntry)),
                    createdAt = now,
                    updatedAt = now
                )
            )
        } catch (e: MongoException) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                return ReportOutcome.ALREADY_REPORTED
            }
            throw e
        }

        if (existingCount > 0) {
            reportRepository.updateSiblingCounts(targetType.shortName, targetObjectId, newSiblingCount)
        }

        log.info("Report submitted for {} {} by user {}", targetType, targetId, reporterId)

        eventPublisher.publishEvent(
            ReportSubmittedEvent(
                reportId = savedDoc.id!!.toHexString(),
                targetId = targetInfo.targetId,
                targetType = targetType.name,
                contextId = targetInfo.contextId,
                reporterId = reporterId,
                reason = reason.name
            )
        )

        return ReportOutcome.REPORT_SUBMITTED
    }

    fun getReporterReports(reporterId: String, page: Int, size: Int): Page<Report> {
        val pageable = PageRequest.of(page, size)
        return reportRepository.findByReporterIdPaginated(ObjectId(reporterId), pageable).map { it.toDomain() }
    }
}
