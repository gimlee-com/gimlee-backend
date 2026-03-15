package com.gimlee.ads.qa.event

import com.gimlee.ads.qa.persistence.AnswerRepository
import com.gimlee.ads.qa.persistence.QuestionRepository
import com.gimlee.ads.qa.persistence.QuestionUpvoteRepository
import com.gimlee.events.AdStatusChangedEvent
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class QaAdLifecycleListener(
    private val questionRepository: QuestionRepository,
    private val answerRepository: AnswerRepository,
    private val questionUpvoteRepository: QuestionUpvoteRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onAdStatusChanged(event: AdStatusChangedEvent) {
        if (event.newStatus == "DELETED") {
            cleanupQaData(event.adId)
        }
    }

    private fun cleanupQaData(adId: String) {
        log.info("Cleaning up Q&A data for deleted ad {}", adId)
        val adObjectId = ObjectId(adId)

        val questionIds = questionRepository.findByAdIdAndStatuses(
            adObjectId,
            listOf("P", "A", "H", "R"),
            org.bson.Document(),
            org.springframework.data.domain.Pageable.unpaged()
        ).content.mapNotNull { it.id }

        questionUpvoteRepository.deleteByQuestionIds(questionIds)
        answerRepository.deleteByQuestionIds(questionIds)
        questionRepository.deleteByAdId(adObjectId)
    }
}
