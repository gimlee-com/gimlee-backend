package com.gimlee.ads.qa.domain

import com.gimlee.reports.domain.model.ReportTargetInfo
import com.gimlee.reports.domain.model.ReportTargetResolver
import com.gimlee.reports.domain.model.ReportTargetType
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
        }
    }

    private fun resolveQuestion(questionId: String): ReportTargetInfo? {
        val question = questionService.getQuestion(questionId) ?: return null
        return ReportTargetInfo(
            targetId = question.id,
            targetType = ReportTargetType.QUESTION,
            contextId = question.adId
        )
    }

    private fun resolveAnswer(answerId: String): ReportTargetInfo? {
        val answer = answerService.getAnswer(answerId) ?: return null
        val question = questionService.getQuestion(answer.questionId) ?: return null
        return ReportTargetInfo(
            targetId = answer.id,
            targetType = ReportTargetType.ANSWER,
            contextId = question.adId
        )
    }
}
