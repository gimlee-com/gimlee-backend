package com.gimlee.ads.event

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.common.toMicros
import com.gimlee.events.AdStatusChangedEvent
import com.gimlee.events.UserBannedEvent
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class UserBanAdDeactivationListener(
    private val adRepository: AdRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleUserBanned(event: UserBannedEvent) {
        val userId = ObjectId(event.userId)
        val now = Instant.now().toMicros()

        val deactivatedAds = adRepository.deactivateAdsByUserId(userId, now)
        if (deactivatedAds.isEmpty()) {
            log.info("User {} banned — no active ads to deactivate", event.userId)
            return
        }

        log.info("User {} banned — deactivated {} active ad(s)", event.userId, deactivatedAds.size)

        for (ad in deactivatedAds) {
            eventPublisher.publishEvent(AdStatusChangedEvent(
                adId = ad.id.toHexString(),
                sellerId = event.userId,
                oldStatus = "ACTIVE",
                newStatus = "INACTIVE",
                categoryIds = ad.categoryIds ?: emptyList(),
                reason = AdStatusChangedEvent.Reason.USER_BANNED
            ))
        }
    }
}
