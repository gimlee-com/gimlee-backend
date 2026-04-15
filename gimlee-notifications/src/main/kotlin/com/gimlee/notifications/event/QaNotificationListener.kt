package com.gimlee.notifications.event

import com.gimlee.events.QuestionAnsweredEvent
import com.gimlee.events.QuestionAskedEvent
import com.gimlee.events.QuestionUpvoteMilestoneEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class QaNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleQuestionAsked(event: QuestionAskedEvent) {
        try {
            val sellerId = event.sellerId
            notificationService.createNotification(
                userId = sellerId,
                type = NotificationType.QA_NEW_QUESTION,
                language = languageProvider.getLanguage(sellerId),
                messageArgs = arrayOf(event.adTitle),
                suggestedAction = SuggestedAction(SuggestedActionType.SELLER_QA_DETAILS, event.adId),
                metadata = mapOf(
                    "adId" to event.adId,
                    "questionId" to event.questionId
                )
            )
        } catch (e: Exception) {
            log.error("Failed to process question asked notification: questionId={}", event.questionId, e)
        }
    }

    @Async
    @EventListener
    fun handleQuestionAnswered(event: QuestionAnsweredEvent) {
        try {
            val questionAuthorId = event.questionAuthorId
            notificationService.createNotification(
                userId = questionAuthorId,
                type = NotificationType.QA_NEW_ANSWER,
                language = languageProvider.getLanguage(questionAuthorId),
                messageArgs = arrayOf(event.adTitle),
                suggestedAction = SuggestedAction(SuggestedActionType.BUYER_QA_DETAILS, event.adId),
                metadata = mapOf(
                    "adId" to event.adId,
                    "questionId" to event.questionId
                )
            )
        } catch (e: Exception) {
            log.error("Failed to process question answered notification: questionId={}", event.questionId, e)
        }
    }

    @Async
    @EventListener
    fun handleUpvoteMilestone(event: QuestionUpvoteMilestoneEvent) {
        try {
            val sellerId = event.sellerId
            notificationService.createNotification(
                userId = sellerId,
                type = NotificationType.QA_UPVOTE_MILESTONE,
                language = languageProvider.getLanguage(sellerId),
                messageArgs = arrayOf(event.adTitle, event.upvoteCount.toString()),
                suggestedAction = SuggestedAction(SuggestedActionType.SELLER_QA_DETAILS, event.adId),
                metadata = mapOf(
                    "adId" to event.adId,
                    "questionId" to event.questionId,
                    "upvoteCount" to event.upvoteCount.toString()
                )
            )
        } catch (e: Exception) {
            log.error("Failed to process upvote milestone notification: questionId={}", event.questionId, e)
        }
    }
}
