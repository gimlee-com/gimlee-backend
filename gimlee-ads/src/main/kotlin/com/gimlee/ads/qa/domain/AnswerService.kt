package com.gimlee.ads.qa.domain

import com.gimlee.ads.qa.config.QaProperties
import com.gimlee.ads.qa.domain.model.Answer
import com.gimlee.ads.qa.domain.model.AnswerType
import com.gimlee.ads.qa.domain.model.QuestionStatus
import com.gimlee.ads.qa.persistence.AnswerRepository
import com.gimlee.ads.qa.persistence.QuestionRepository
import com.gimlee.ads.qa.persistence.model.AnswerDocument
import com.gimlee.common.toMicros
import com.gimlee.events.QuestionAnsweredEvent
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AnswerService(
    private val answerRepository: AnswerRepository,
    private val questionRepository: QuestionRepository,
    private val qaProperties: QaProperties,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submitSellerAnswer(questionId: String, authorId: String, adTitle: String, text: String): Pair<QaOutcome, Answer?> {
        if (text.length > qaProperties.answerMaxLength) {
            return QaOutcome.ANSWER_TOO_LONG to null
        }

        val questionObjectId = ObjectId(questionId)
        val question = questionRepository.findById(questionObjectId)
            ?: return QaOutcome.QUESTION_NOT_FOUND to null

        if (question.status != QuestionStatus.PENDING.shortName && question.status != QuestionStatus.ANSWERED.shortName) {
            return QaOutcome.QUESTION_NOT_FOUND to null
        }

        val existingSeller = answerRepository.findSellerAnswerByQuestionId(questionObjectId)
        if (existingSeller != null) {
            return QaOutcome.ANSWER_ALREADY_EXISTS to null
        }

        val now = Instant.now().toMicros()
        val doc = answerRepository.save(
            AnswerDocument(
                questionId = questionObjectId,
                authorId = ObjectId(authorId),
                type = AnswerType.SELLER.shortName,
                text = text,
                createdAt = now,
                updatedAt = now
            )
        )

        if (question.status == QuestionStatus.PENDING.shortName) {
            questionRepository.updateStatus(questionObjectId, QuestionStatus.ANSWERED.shortName, now)
        }

        val answer = doc.toDomain()
        log.info("Seller answer {} submitted for question {}", answer.id, questionId)

        eventPublisher.publishEvent(
            QuestionAnsweredEvent(
                questionId = questionId,
                answerId = answer.id,
                adId = question.adId.toHexString(),
                adTitle = adTitle,
                questionAuthorId = question.authorId.toHexString(),
                answerAuthorId = authorId,
                answerType = AnswerType.SELLER.name
            )
        )

        return QaOutcome.ANSWER_CREATED to answer
    }

    fun submitCommunityAnswer(questionId: String, authorId: String, adTitle: String, text: String): Pair<QaOutcome, Answer?> {
        if (text.length > qaProperties.answerMaxLength) {
            return QaOutcome.ANSWER_TOO_LONG to null
        }

        val questionObjectId = ObjectId(questionId)
        val question = questionRepository.findById(questionObjectId)
            ?: return QaOutcome.QUESTION_NOT_FOUND to null

        if (question.status != QuestionStatus.ANSWERED.shortName) {
            return QaOutcome.QUESTION_NOT_FOUND to null
        }

        val communityCount = answerRepository.countCommunityAnswersByQuestionId(questionObjectId)
        if (communityCount >= qaProperties.maxCommunityAnswersPerQuestion) {
            return QaOutcome.ANSWER_COMMUNITY_LIMIT to null
        }

        val now = Instant.now().toMicros()
        val doc = answerRepository.save(
            AnswerDocument(
                questionId = questionObjectId,
                authorId = ObjectId(authorId),
                type = AnswerType.COMMUNITY.shortName,
                text = text,
                createdAt = now,
                updatedAt = now
            )
        )

        val answer = doc.toDomain()
        log.info("Community answer {} submitted for question {}", answer.id, questionId)

        eventPublisher.publishEvent(
            QuestionAnsweredEvent(
                questionId = questionId,
                answerId = answer.id,
                adId = question.adId.toHexString(),
                adTitle = adTitle,
                questionAuthorId = question.authorId.toHexString(),
                answerAuthorId = authorId,
                answerType = AnswerType.COMMUNITY.name
            )
        )

        return QaOutcome.ANSWER_CREATED to answer
    }

    fun editAnswer(answerId: String, authorId: String, newText: String): Pair<QaOutcome, Answer?> {
        if (newText.length > qaProperties.answerMaxLength) {
            return QaOutcome.ANSWER_TOO_LONG to null
        }

        val answerObjectId = ObjectId(answerId)
        val answer = answerRepository.findById(answerObjectId)
            ?: return QaOutcome.ANSWER_NOT_FOUND to null

        if (answer.authorId.toHexString() != authorId) {
            return QaOutcome.ANSWER_NOT_OWNER to null
        }

        val now = Instant.now().toMicros()
        answerRepository.update(answerObjectId, newText, now)

        val updated = answer.copy(text = newText, updatedAt = now).toDomain()
        return QaOutcome.ANSWER_UPDATED to updated
    }

    fun getAnswersForQuestions(questionIds: List<String>): Map<String, List<Answer>> {
        if (questionIds.isEmpty()) return emptyMap()
        val objectIds = questionIds.map { ObjectId(it) }
        return answerRepository.findByQuestionIds(objectIds)
            .mapKeys { it.key.toHexString() }
            .mapValues { entry -> entry.value.map { it.toDomain() } }
    }

    fun getAnswer(answerId: String): Answer? {
        return try {
            answerRepository.findById(ObjectId(answerId))?.toDomain()
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
