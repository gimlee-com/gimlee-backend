package com.gimlee.ads.qa.domain

import com.gimlee.ads.qa.config.QaProperties
import com.gimlee.ads.qa.persistence.QuestionRepository
import com.gimlee.ads.qa.persistence.QuestionUpvoteRepository
import com.gimlee.events.QuestionUpvoteMilestoneEvent
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class UpvoteService(
    private val questionUpvoteRepository: QuestionUpvoteRepository,
    private val questionRepository: QuestionRepository,
    private val qaProperties: QaProperties,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun toggleUpvote(questionId: String, userId: String, sellerId: String, adTitle: String): QaOutcome {
        val questionObjectId = ObjectId(questionId)
        val userObjectId = ObjectId(userId)

        val question = questionRepository.findById(questionObjectId)
            ?: return QaOutcome.QUESTION_NOT_FOUND

        val isNew = questionUpvoteRepository.upsert(questionObjectId, userObjectId)
        if (isNew) {
            questionRepository.incrementUpvoteCount(questionObjectId, 1)
            val newCount = question.upvoteCount + 1
            checkMilestone(questionId, question.adId.toHexString(), sellerId, adTitle, newCount)
        } else {
            questionUpvoteRepository.delete(questionObjectId, userObjectId)
            questionRepository.incrementUpvoteCount(questionObjectId, -1)
        }

        return QaOutcome.UPVOTE_TOGGLED
    }

    fun getUserUpvotes(userId: String, questionIds: List<String>): Set<String> {
        if (questionIds.isEmpty()) return emptySet()
        val objectIds = questionIds.map { ObjectId(it) }
        return questionUpvoteRepository.findUpvotedQuestionIds(ObjectId(userId), objectIds)
            .map { it.toHexString() }
            .toSet()
    }

    private fun checkMilestone(questionId: String, adId: String, sellerId: String, adTitle: String, newCount: Int) {
        if (newCount in qaProperties.upvoteMilestones) {
            log.info("Question {} reached upvote milestone: {}", questionId, newCount)
            eventPublisher.publishEvent(
                QuestionUpvoteMilestoneEvent(
                    questionId = questionId,
                    adId = adId,
                    adTitle = adTitle,
                    sellerId = sellerId,
                    upvoteCount = newCount
                )
            )
        }
    }
}
