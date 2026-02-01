package com.gimlee.user.domain

import com.gimlee.common.toMicros
import com.gimlee.user.config.UserPresenceProperties
import com.gimlee.user.domain.model.UserPresence
import com.gimlee.user.domain.model.UserPresenceStatus
import com.gimlee.user.persistence.UserPresenceRepository
import com.gimlee.user.persistence.model.UserPresenceDocument
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class UserPresenceService(
    private val repository: UserPresenceRepository,
    private val properties: UserPresenceProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ConcurrentHashMap<String, Long>()

    fun trackActivity(userId: String, timestamp: Instant = Instant.now()) {
        buffer[userId] = timestamp.toMicros()
    }

    fun getUserPresence(userId: String): UserPresence {
        val document = repository.findByUserId(userId)
        val lastSeenAt = buffer[userId] ?: document?.lastSeenAt ?: 0L
        
        val status = if (document?.status != null) {
            val persistedStatus = UserPresenceStatus.fromShortName(document.status)
            if (persistedStatus == UserPresenceStatus.ONLINE || persistedStatus == UserPresenceStatus.OFFLINE) {
                inferStatus(lastSeenAt)
            } else {
                if (isOnline(lastSeenAt)) persistedStatus else UserPresenceStatus.OFFLINE
            }
        } else {
            inferStatus(lastSeenAt)
        }

        return UserPresence(
            userId = userId,
            lastSeenAt = lastSeenAt,
            status = status,
            customStatus = document?.customStatus
        )
    }

    fun updateStatus(userId: String, status: UserPresenceStatus, customStatus: String? = null) {
        val current = repository.findByUserId(userId)
        val document = UserPresenceDocument(
            userId = org.bson.types.ObjectId(userId),
            lastSeenAt = buffer[userId] ?: current?.lastSeenAt ?: Instant.now().toMicros(),
            status = status.shortName,
            customStatus = customStatus
        )
        repository.save(document)
    }

    @Scheduled(fixedDelayString = "\${gimlee.user.presence.flush-interval-ms:60000}")
    fun flushBuffer() {
        if (buffer.isEmpty()) return

        val snapshot = HashMap(buffer)
        buffer.clear()

        try {
            repository.bulkUpdateLastSeen(snapshot)
            log.debug("Flushed {} user activities to database", snapshot.size)
        } catch (e: Exception) {
            log.error("Failed to flush user activity buffer", e)
        }
    }

    private fun inferStatus(lastSeenAt: Long): UserPresenceStatus {
        return if (isOnline(lastSeenAt)) UserPresenceStatus.ONLINE else UserPresenceStatus.OFFLINE
    }

    private fun isOnline(lastSeenAt: Long): Boolean {
        val threshold = Instant.now().minusSeconds(properties.onlineThresholdMinutes * 60).toMicros()
        return lastSeenAt > threshold
    }
}
