package com.gimlee.auth.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.gimlee.auth.domain.User.Companion.FIELD_ID
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.persistence.UserBanRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.common.toMicros
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class BannedUserCache(
    private val userRepository: UserRepository,
    private val userBanRepository: UserBanRepository,
    @Value("\${gimlee.auth.ban.cache.ttl-seconds:300}")
    ttlSeconds: Long,
    @Value("\${gimlee.auth.ban.cache.max-size:10000}")
    maxSize: Long
) {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(maxSize)
        .build<String, Boolean>()

    fun isBanned(userId: String): Boolean {
        return cache.get(userId) { loadBanStatus(it) } ?: false
    }

    fun invalidate(userId: String) {
        cache.invalidate(userId)
    }

    private fun loadBanStatus(userId: String): Boolean {
        val user = userRepository.findOneByField(FIELD_ID, ObjectId(userId))
            ?: return false

        if (user.status != UserStatus.BANNED) return false

        val activeBan = userBanRepository.findActiveByUserId(userId)
        if (activeBan?.bannedUntil != null && activeBan.bannedUntil <= Instant.now().toMicros()) {
            return false
        }

        return true
    }
}
