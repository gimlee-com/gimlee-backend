package com.gimlee.auth.domain

import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.persistence.UserBanRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.events.UserUnbannedEvent
import org.apache.logging.log4j.LogManager
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BanExpiryJob(
    private val userBanRepository: UserBanRepository,
    private val userRepository: UserRepository,
    private val bannedUserCache: BannedUserCache,
    private val eventPublisher: ApplicationEventPublisher
) {

    companion object {
        private val log = LogManager.getLogger()
        private const val SYSTEM_ACTOR = "SYSTEM"
    }

    @Scheduled(fixedDelayString = "\${gimlee.auth.ban.expiry-check-interval-ms:60000}")
    fun processExpiredBans() {
        val expiredBans = userBanRepository.findExpiredActiveBans()
        if (expiredBans.isEmpty()) return

        log.info("Processing {} expired ban(s)", expiredBans.size)

        for (ban in expiredBans) {
            try {
                userBanRepository.deactivate(ban.id, SYSTEM_ACTOR)
                userRepository.updateStatus(ObjectId(ban.userId), UserStatus.ACTIVE)
                bannedUserCache.invalidate(ban.userId)

                eventPublisher.publishEvent(UserUnbannedEvent(
                    userId = ban.userId,
                    unbannedBy = SYSTEM_ACTOR
                ))

                log.info("Temporary ban {} for user {} has expired and been lifted", ban.id, ban.userId)
            } catch (e: Exception) {
                log.error("Failed to process expired ban {} for user {}", ban.id, ban.userId, e)
            }
        }
    }
}
