package com.gimlee.analytics.domain

import com.gimlee.analytics.persistence.AnalyticsEventRepository
import com.gimlee.analytics.persistence.model.AnalyticsEventDocument
import com.gimlee.events.GenericAnalyticsEvent
import org.bson.types.ObjectId
import org.springframework.stereotype.Service

@Service
class AnalyticsEventService(
    private val analyticsEventRepository: AnalyticsEventRepository
) {
    fun recordEvent(event: GenericAnalyticsEvent) {
        val document = AnalyticsEventDocument(
            id = ObjectId(),
            type = event.type,
            targetId = event.targetId,
            timestampMicros = event.timestampMicros,
            sampleRate = event.sampleRate,
            userId = event.userId?.takeIf { it.isNotBlank() }?.let { ObjectId(it) },
            clientId = event.clientId,
            botScore = event.botScore,
            userAgent = event.userAgent,
            referrer = event.referrer,
            metadata = event.metadata.ifEmpty { null }
        )
        analyticsEventRepository.save(document)
    }
}
