package com.gimlee.notifications.domain

import com.gimlee.common.toMicros
import com.gimlee.notifications.persistence.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class NotificationCleanupJob(
    private val notificationRepository: NotificationRepository,
    @Value("\${gimlee.notifications.retention-days:90}")
    private val retentionDays: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${gimlee.notifications.cleanup.cron:0 0 3 * * ?}")
    fun cleanupOldNotifications() {
        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toMicros()
        val deleted = notificationRepository.deleteOlderThan(cutoff)
        if (deleted > 0) {
            log.info("Notification cleanup: deleted {} notifications older than {} days", deleted, retentionDays)
        }
    }
}
