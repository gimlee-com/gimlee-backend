package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.persistence.SystemMetadataRepository
import com.gimlee.common.toMicros
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class CategoryPopularityGroundingService(
    private val adRepository: AdRepository,
    private val categoryRepository: CategoryRepository,
    private val metadataRepository: SystemMetadataRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired @Lazy private lateinit var self: CategoryPopularityGroundingService

    companion object {
        private const val LAST_GROUNDING_KEY = "last_category_popularity_grounding"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun runOnStartup() {
        if (metadataRepository.getTimestamp(LAST_GROUNDING_KEY) == null) {
            log.info("Initial category popularity grounding required. Triggering task.")
            self.groundPopularity()
        }
    }

    @Scheduled(cron = "\${gimlee.ads.category.grounding.cron:0 0 0 * * MON}", zone = "UTC")
    @SchedulerLock(name = "categoryPopularityGrounding", lockAtMostFor = "PT1H", lockAtLeastFor = "\${gimlee.ads.category.grounding.lock.at-least:PT0S}")
    fun groundPopularity() {
        log.info("Starting category popularity grounding (healing the drift)...")
        val startTime = Instant.now()
        try {
            val counts = adRepository.countAdsPerCategory()
            categoryRepository.resetAllPopularity()
            counts.forEach { (id, count) ->
                categoryRepository.updatePopularity(id, count)
            }
            
            metadataRepository.setTimestamp(LAST_GROUNDING_KEY, Instant.now().toMicros())
            log.info("Category popularity grounding completed in {}ms", Duration.between(startTime, Instant.now()).toMillis())
        } catch (e: Exception) {
            log.error("Category popularity grounding task failed", e)
        }
    }
}
