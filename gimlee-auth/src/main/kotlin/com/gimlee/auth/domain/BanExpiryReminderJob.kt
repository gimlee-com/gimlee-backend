package com.gimlee.auth.domain

import com.gimlee.auth.config.BanProperties
import com.gimlee.auth.persistence.UserBanRepository
import com.gimlee.common.toMicros
import com.gimlee.events.BanExpiryApproachingEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class BanExpiryReminderJob(
    private val userBanRepository: UserBanRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val banProperties: BanProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gimlee.auth.ban.expiry-reminder.check-interval-ms:300000}")
    fun checkExpiringSoonBans() {
        val now = Instant.now()
        val windowHours = banProperties.expiryReminder.windowHours
        val ceiling = now.plus(windowHours, ChronoUnit.HOURS).toMicros()

        val candidates = userBanRepository.findBansExpiringSoon(ceiling)
        if (candidates.isEmpty()) return

        log.debug("Found {} ban(s) expiring within {} hours", candidates.size, windowHours)

        for (ban in candidates) {
            if (userBanRepository.markReminderSent(ban.id)) {
                eventPublisher.publishEvent(
                    BanExpiryApproachingEvent(
                        userId = ban.userId,
                        bannedUntil = ban.bannedUntil!!
                    )
                )
                log.debug("Published ban expiry approaching event: userId={}", ban.userId)
            }
        }
    }
}
