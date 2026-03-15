package com.gimlee.ads.qa.domain

import com.gimlee.ads.qa.domain.model.QaReportTargetType
import com.gimlee.ads.qa.persistence.QaReportRepository
import com.gimlee.ads.qa.persistence.model.QaReportDocument
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.gimlee.common.toMicros
import com.gimlee.events.QaContentReportedEvent
import com.mongodb.MongoException
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class QaReportService(
    private val qaReportRepository: QaReportRepository,
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun reportQuestion(questionId: String, reporterId: String, reason: String): QaOutcome {
        val question = questionService.getQuestion(questionId) ?: return QaOutcome.QUESTION_NOT_FOUND

        return submitReport(
            targetId = questionId,
            targetType = QaReportTargetType.QUESTION,
            adId = question.adId,
            reporterId = reporterId,
            reason = reason
        )
    }

    fun reportAnswer(answerId: String, reporterId: String, reason: String): QaOutcome {
        val answer = answerService.getAnswer(answerId) ?: return QaOutcome.ANSWER_NOT_FOUND
        val question = questionService.getQuestion(answer.questionId) ?: return QaOutcome.QUESTION_NOT_FOUND

        return submitReport(
            targetId = answerId,
            targetType = QaReportTargetType.ANSWER,
            adId = question.adId,
            reporterId = reporterId,
            reason = reason
        )
    }

    private fun submitReport(
        targetId: String,
        targetType: QaReportTargetType,
        adId: String,
        reporterId: String,
        reason: String
    ): QaOutcome {
        val targetObjectId = ObjectId(targetId)
        val reporterObjectId = ObjectId(reporterId)

        val now = Instant.now().toMicros()
        try {
            qaReportRepository.save(
                QaReportDocument(
                    targetId = targetObjectId,
                    targetType = targetType.shortName,
                    adId = ObjectId(adId),
                    reporterId = reporterObjectId,
                    reason = reason,
                    createdAt = now
                )
            )
        } catch (e: MongoException) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                return QaOutcome.ALREADY_REPORTED
            }
            throw e
        }

        log.info("Report submitted for {} {} by user {}", targetType, targetId, reporterId)

        eventPublisher.publishEvent(
            QaContentReportedEvent(
                targetId = targetId,
                targetType = targetType.name,
                adId = adId,
                reporterId = reporterId,
                reason = reason
            )
        )

        return QaOutcome.REPORT_SUBMITTED
    }
}
