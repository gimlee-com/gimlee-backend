package com.gimlee.payments.exchange.domain

import com.gimlee.common.toMicros
import com.gimlee.payments.persistence.ExchangeRateRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ExchangeRateCleanupService(
    private val exchangeRateRepository: ExchangeRateRepository,
    @Value("\${gimlee.payments.exchange.cleanup.retention-days:365}")
    private val retentionDays: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${gimlee.payments.exchange.cleanup.cron:0 0 1 * * ?}")
    @SchedulerLock(
        name = "exchangeRateCleanup",
        lockAtMostFor = "\${gimlee.payments.exchange.cleanup.lock.at-most:PT1H}",
        lockAtLeastFor = "\${gimlee.payments.exchange.cleanup.lock.at-least:PT5M}"
    )
    fun cleanupOldRates() {
        log.info("Starting exchange rates cleanup...")
        val threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toMicros()
        val deletedCount = exchangeRateRepository.deleteOlderThan(threshold)
        log.info("Exchange rates cleanup completed. Total rates deleted: $deletedCount")
    }
}
