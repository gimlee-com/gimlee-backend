package com.gimlee.ads.qa.domain

import com.gimlee.support.report.domain.model.ReportTargetInfo
import com.gimlee.support.report.domain.model.ReportTargetResolver
import com.gimlee.support.report.domain.model.ReportTargetType
import org.springframework.stereotype.Component

@Component
class QaReportTargetResolver(
    private val questionService: QuestionService,
    private val answerService: AnswerService
) : ReportTargetResolver {

    override fun supports(targetType: ReportTargetType): Boolean =
        targetType == ReportTargetType.QUESTION || targetType == ReportTargetType.ANSWER

    override fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo? {
        return when (targetType) {
            ReportTargetType.QUESTION -> resolveQuestion(targetId)
            ReportTargetType.ANSWER -> resolveAnswer(targetId)
            else -> null
        }
    }

    private fun resolveQuestion(questionId: String): ReportTargetInfo? {
        val question = questionService.getQuestion(questionId) ?: return null
        return ReportTargetInfo(
            targetId = question.id,
            targetType = ReportTargetType.QUESTION,
            contextId = question.adId,
            targetTitle = question.text.take(100),
            snapshot = mapOf(
                "adId" to question.adId,
                "authorId" to question.authorId,
                "text" to question.text,
                "status" to question.status.name
            )
        )
    }

    private fun resolveAnswer(answerId: String): ReportTargetInfo? {
        val answer = answerService.getAnswer(answerId) ?: return null
        val question = questionService.getQuestion(answer.questionId) ?: return null
        return ReportTargetInfo(
            targetId = answer.id,
            targetType = ReportTargetType.ANSWER,
            contextId = question.adId,
            targetTitle = answer.text.take(100),
            snapshot = mapOf(
                "questionId" to answer.questionId,
                "adId" to question.adId,
                "authorId" to answer.authorId,
                "text" to answer.text,
                "type" to answer.type.name
            )
        )
    }
}
