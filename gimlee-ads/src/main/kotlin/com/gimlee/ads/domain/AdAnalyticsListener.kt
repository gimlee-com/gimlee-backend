package com.gimlee.ads.domain

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.events.GenericAnalyticsEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZoneId

@Component
class AdAnalyticsListener(
    private val adVisitService: AdVisitService
) {
    companion object {
        const val EVENT_TYPE_AD_VIEW = "AD_VIEW"
    }

    @Async
    @EventListener
    fun handleAdAnalyticsEvent(event: GenericAnalyticsEvent) {
        if (event.type == EVENT_TYPE_AD_VIEW && event.targetId != null && event.clientId != null) {
            val date = fromMicros(event.timestampMicros).atZone(ZoneId.systemDefault()).toLocalDate()
            adVisitService.recordVisit(event.targetId!!, event.clientId!!, date)
        }
    }
}
