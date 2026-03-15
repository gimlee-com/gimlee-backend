package com.gimlee.reports.domain

import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.gimlee.common.toMicros
import com.gimlee.events.ReportSubmittedEvent
import com.gimlee.reports.domain.model.ReportTargetResolver
import com.gimlee.reports.domain.model.ReportTargetType
import com.gimlee.reports.persistence.ReportRepository
import com.gimlee.reports.persistence.model.ReportDocument
import com.mongodb.MongoException
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val resolvers: List<ReportTargetResolver>,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitReport(targetType: ReportTargetType, targetId: String, reporterId: String, reason: String): ReportOutcome {
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
        try {
            reportRepository.save(
                ReportDocument(
                    targetId = targetObjectId,
                    targetType = targetType.shortName,
                    contextId = targetInfo.contextId?.let { ObjectId(it) },
                    reporterId = reporterObjectId,
                    reason = reason,
                    createdAt = now
                )
            )
        } catch (e: MongoException) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                return ReportOutcome.ALREADY_REPORTED
            }
            throw e
        }

        log.info("Report submitted for {} {} by user {}", targetType, targetId, reporterId)

        eventPublisher.publishEvent(
            ReportSubmittedEvent(
                targetId = targetInfo.targetId,
                targetType = targetType.name,
                contextId = targetInfo.contextId,
                reporterId = reporterId,
                reason = reason
            )
        )

        return ReportOutcome.REPORT_SUBMITTED
    }
}
