package com.gimlee.auth.domain

import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserBanRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.UUIDv7
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.events.UserBannedEvent
import com.gimlee.events.UserUnbannedEvent
import org.apache.logging.log4j.LogManager
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BanService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val userBanRepository: UserBanRepository,
    private val bannedUserCache: BannedUserCache,
    private val eventPublisher: ApplicationEventPublisher
) {

    companion object {
        private val log = LogManager.getLogger()
    }

    fun banUser(userId: String, reason: String, bannedUntil: Long?, adminUserId: String): Outcome {
        val userObjectId = ObjectId(userId)
        val user = userRepository.findOneByField(User.FIELD_ID, userObjectId)
            ?: return AdminUserOutcome.USER_NOT_FOUND

        if (user.status == UserStatus.BANNED) {
            return AdminUserOutcome.USER_ALREADY_BANNED
        }

        val roles = userRoleRepository.getAll(userObjectId)
        if (Role.ADMIN in roles) {
            return AdminUserOutcome.CANNOT_BAN_ADMIN
        }

        val now = Instant.now().toMicros()
        val ban = UserBan(
            id = UUIDv7.generate().toString(),
            userId = userId,
            reason = reason,
            bannedBy = adminUserId,
            bannedAt = now,
            bannedUntil = bannedUntil,
            unbannedBy = null,
            unbannedAt = null,
            active = true
        )

        userRepository.updateStatus(userObjectId, UserStatus.BANNED)
        userBanRepository.save(ban)
        bannedUserCache.invalidate(userId)

        log.info("User {} banned by admin {} — reason: {}", userId, adminUserId, reason)

        eventPublisher.publishEvent(UserBannedEvent(
            userId = userId,
            reason = reason,
            bannedBy = adminUserId,
            bannedUntil = bannedUntil
        ))

        return AdminUserOutcome.USER_BANNED_SUCCESSFULLY
    }

    fun unbanUser(userId: String, adminUserId: String): Outcome {
        val userObjectId = ObjectId(userId)
        val user = userRepository.findOneByField(User.FIELD_ID, userObjectId)
            ?: return AdminUserOutcome.USER_NOT_FOUND

        if (user.status != UserStatus.BANNED) {
            return AdminUserOutcome.USER_NOT_BANNED
        }

        val activeBan = userBanRepository.findActiveByUserId(userId)
        if (activeBan != null) {
            userBanRepository.deactivate(activeBan.id, adminUserId)
        }

        userRepository.updateStatus(userObjectId, UserStatus.ACTIVE)
        bannedUserCache.invalidate(userId)

        log.info("User {} unbanned by admin {}", userId, adminUserId)

        eventPublisher.publishEvent(UserUnbannedEvent(
            userId = userId,
            unbannedBy = adminUserId
        ))

        return AdminUserOutcome.USER_UNBANNED_SUCCESSFULLY
    }

    fun getActiveBan(userId: String): UserBan? {
        return userBanRepository.findActiveByUserId(userId)
    }

    fun getBanHistory(userId: String): List<UserBan> {
        return userBanRepository.findAllByUserId(userId)
    }

    fun isBanned(userId: String): Boolean {
        return bannedUserCache.isBanned(userId)
    }
}
