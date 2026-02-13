package com.gimlee.analytics.domain

import com.gimlee.events.GenericAnalyticsEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AnalyticsEventListener(
    private val analyticsEventService: AnalyticsEventService
) {
    @Async
    @EventListener
    fun handleAnalyticsEvent(event: GenericAnalyticsEvent) {
        analyticsEventService.recordEvent(event)
    }
}
