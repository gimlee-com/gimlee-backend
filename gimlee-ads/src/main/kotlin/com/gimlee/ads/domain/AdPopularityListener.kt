package com.gimlee.ads.domain

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.events.AdStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AdPopularityListener(private val categoryRepository: CategoryRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleAdStatusChanged(event: AdStatusChangedEvent) {
        if (event.oldStatus == event.newStatus) return

        if (event.newStatus == AdStatus.ACTIVE.name) {
            log.debug("Incrementing popularity for categories: {}", event.categoryIds)
            categoryRepository.incrementPopularity(event.categoryIds, 1L)
        } else if (event.oldStatus == AdStatus.ACTIVE.name) {
            log.debug("Decrementing popularity for categories: {}", event.categoryIds)
            categoryRepository.incrementPopularity(event.categoryIds, -1L)
        }
    }
}
