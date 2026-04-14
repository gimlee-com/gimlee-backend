package com.gimlee.ads.qa.domain

import com.gimlee.ads.qa.config.QaProperties
import com.gimlee.ads.qa.domain.model.Question
import com.gimlee.ads.qa.domain.model.QuestionStatus
import com.gimlee.ads.qa.persistence.QuestionRepository
import com.gimlee.ads.qa.persistence.model.QuestionDocument
import com.gimlee.common.InstantUtils
import com.gimlee.common.toMicros
import com.gimlee.events.QuestionAskedEvent
import com.mongodb.client.model.Sorts
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val qaProperties: QaProperties,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun askQuestion(adId: String, authorId: String, sellerId: String, adTitle: String, text: String): Pair<QaOutcome, Question?> {
        if (text.length > qaProperties.questionMaxLength) {
            return QaOutcome.QUESTION_TOO_LONG to null
        }

        val adObjectId = ObjectId(adId)
        val authorObjectId = ObjectId(authorId)

        val unansweredCount = questionRepository.countUnansweredByAuthorAndAd(authorObjectId, adObjectId)
        if (unansweredCount >= qaProperties.maxUnansweredPerUserPerAd) {
            return QaOutcome.QUESTION_LIMIT_REACHED to null
        }

        val latestQuestion = questionRepository.findLatestByAuthorAndAd(authorObjectId, adObjectId)
        if (latestQuestion != null) {
            val latestTime = InstantUtils.fromMicros(latestQuestion.createdAt)
            if (Instant.now().epochSecond - latestTime.epochSecond < qaProperties.cooldownSeconds) {
                return QaOutcome.QUESTION_COOLDOWN_ACTIVE to null
            }
        }

        val totalCount = questionRepository.countByAdId(adObjectId)
        if (totalCount >= qaProperties.maxQuestionsPerAd) {
            return QaOutcome.QUESTION_AD_TOTAL_LIMIT to null
        }

        val now = Instant.now().toMicros()
        val doc = questionRepository.save(
            QuestionDocument(
                adId = adObjectId,
                authorId = authorObjectId,
                text = text,
                upvoteCount = 0,
                isPinned = false,
                status = QuestionStatus.PENDING.shortName,
                createdAt = now,
                updatedAt = now
            )
        )

        val question = doc.toDomain()
        log.info("Question {} asked on ad {} by user {}", question.id, adId, authorId)

        eventPublisher.publishEvent(
            QuestionAskedEvent(
                questionId = question.id,
                adId = adId,
                adTitle = adTitle,
                authorId = authorId,
                sellerId = sellerId
            )
        )

        return QaOutcome.QUESTION_CREATED to question
    }

    fun getPublicQuestions(adId: String, pageable: Pageable, sortBy: String): Page<Question> {
        val sort = when (sortBy.uppercase()) {
            "RECENT" -> Sorts.descending(QuestionDocument.FIELD_CREATED_AT)
            else -> Sorts.descending(QuestionDocument.FIELD_UPVOTE_COUNT)
        }
        return questionRepository.findByAdIdAndStatuses(
            ObjectId(adId),
            listOf(QuestionStatus.ANSWERED.shortName),
            sort,
            pageable
        ).map { it.toDomain() }
    }

    fun getUnansweredQuestions(adId: String, pageable: Pageable): Page<Question> {
        return questionRepository.findUnansweredByAdId(ObjectId(adId), pageable)
            .map { it.toDomain() }
    }

    fun getOwnUnansweredQuestions(adId: String, authorId: String): List<Question> {
        return questionRepository.findByAdIdAndAuthorId(
            ObjectId(adId),
            ObjectId(authorId),
            QuestionStatus.PENDING.shortName
        ).map { it.toDomain() }
    }

    fun getPinnedQuestions(adId: String): List<Question> {
        return questionRepository.findPinnedByAdId(ObjectId(adId))
            .map { it.toDomain() }
    }

    fun getQuestion(questionId: String): Question? {
        return try {
            questionRepository.findById(ObjectId(questionId))?.toDomain()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun hideQuestion(questionId: String): QaOutcome {
        val question = questionRepository.findById(ObjectId(questionId))
            ?: return QaOutcome.QUESTION_NOT_FOUND
        if (question.status != QuestionStatus.PENDING.shortName && question.status != QuestionStatus.ANSWERED.shortName) {
            return QaOutcome.QUESTION_NOT_FOUND
        }
        questionRepository.updateStatus(question.id!!, QuestionStatus.HIDDEN.shortName, Instant.now().toMicros())
        return QaOutcome.QUESTION_HIDDEN
    }

    fun removeQuestion(questionId: String): QaOutcome {
        val question = questionRepository.findById(ObjectId(questionId))
            ?: return QaOutcome.QUESTION_NOT_FOUND
        questionRepository.updateStatus(question.id!!, QuestionStatus.REMOVED.shortName, Instant.now().toMicros())
        return QaOutcome.QUESTION_REMOVED
    }

    fun togglePin(questionId: String, adId: String): QaOutcome {
        val question = questionRepository.findById(ObjectId(questionId))
            ?: return QaOutcome.QUESTION_NOT_FOUND

        val newPinned = !question.isPinned
        if (newPinned) {
            val pinnedCount = questionRepository.findPinnedByAdId(ObjectId(adId)).size
            if (pinnedCount >= qaProperties.maxPinnedPerAd) {
                return QaOutcome.PIN_LIMIT_REACHED
            }
        }

        questionRepository.updatePinned(question.id!!, newPinned, Instant.now().toMicros())
        return QaOutcome.PIN_TOGGLED
    }

    fun getQaStats(adId: String): Pair<Long, Long> {
        val adObjectId = ObjectId(adId)
        val answered = questionRepository.countByAdIdAndStatus(adObjectId, QuestionStatus.ANSWERED.shortName)
        val unanswered = questionRepository.countByAdIdAndStatus(adObjectId, QuestionStatus.PENDING.shortName)
        return answered to unanswered
    }
}
