package com.gimlee.api.web.admin

import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.persistence.AdRepository
import com.gimlee.api.web.dto.admin.AdminBanDto
import com.gimlee.api.web.dto.admin.AdminUserDetailDto
import com.gimlee.api.web.dto.admin.AdminUserListItemDto
import com.gimlee.api.web.dto.admin.AdminUserStatsDto
import com.gimlee.auth.domain.AdminUserOutcome
import com.gimlee.auth.domain.BanService
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserBan
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.service.UserService
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.PurchaseRepository
import com.gimlee.user.domain.ProfileService
import com.gimlee.user.domain.UserPreferencesService
import com.gimlee.user.domain.UserPresenceService
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val userService: UserService,
    private val banService: BanService,
    private val profileService: ProfileService,
    private val preferencesService: UserPreferencesService,
    private val presenceService: UserPresenceService,
    private val adRepository: AdRepository,
    private val purchaseRepository: PurchaseRepository
) {

    fun listUsers(
        search: String?,
        status: com.gimlee.auth.domain.UserStatus?,
        sort: String?,
        direction: String?,
        page: Int,
        size: Int
    ): Page<AdminUserListItemDto> {
        val pageable = PageRequest.of(page, size)
        val usersPage = userRepository.findAllPaginated(search, status, null, sort, direction, pageable)

        val userIds = usersPage.content.mapNotNull { it.id?.toHexString() }
        val avatars = profileService.getAvatarsByUserIds(userIds)
        val rolesByUser = usersPage.content.associate { user ->
            val id = user.id!!
            id to userRoleRepository.getAll(id)
        }

        return usersPage.map { user ->
            val id = user.id!!
            AdminUserListItemDto(
                userId = id.toHexString(),
                username = user.username,
                displayName = user.displayName,
                email = user.email,
                status = user.status,
                roles = rolesByUser[id] ?: emptyList(),
                lastLogin = user.lastLogin?.toInstant(ZoneOffset.UTC),
                avatarUrl = avatars[id.toHexString()]
            )
        }
    }

    fun getUserDetail(userId: String): Pair<Outcome, AdminUserDetailDto?> {
        val userObjectId = ObjectId(userId)
        val user = userRepository.findOneByField(User.FIELD_ID, userObjectId)
            ?: return Pair(AdminUserOutcome.USER_NOT_FOUND, null)

        val roles = userRoleRepository.getAll(userObjectId)
        val avatar = profileService.getProfile(userId)?.avatarUrl
        val preferences = try { preferencesService.getUserPreferences(userId) } catch (_: Exception) { null }
        val presence = try { presenceService.getUserPresence(userId) } catch (_: Exception) { null }
        val activeBan = banService.getActiveBan(userId)
        val stats = buildUserStats(userObjectId)

        return Pair(CommonOutcome.SUCCESS, AdminUserDetailDto(
            userId = userId,
            username = user.username,
            displayName = user.displayName,
            email = user.email,
            phone = user.phone,
            status = user.status,
            roles = roles,
            lastLogin = user.lastLogin?.toInstant(ZoneOffset.UTC),
            avatarUrl = avatar,
            language = preferences?.language,
            preferredCurrency = preferences?.preferredCurrency,
            lastSeenAt = presence?.lastSeenAt,
            stats = stats,
            activeBan = activeBan?.let { toBanDto(it) }
        ))
    }

    fun banUser(userId: String, reason: String, bannedUntil: Long?, adminUserId: String): Outcome {
        return banService.banUser(userId, reason, bannedUntil, adminUserId)
    }

    fun unbanUser(userId: String, adminUserId: String): Outcome {
        return banService.unbanUser(userId, adminUserId)
    }

    fun getBanHistory(userId: String): Pair<Outcome, List<AdminBanDto>?> {
        userRepository.findOneByField(User.FIELD_ID, ObjectId(userId))
            ?: return Pair(AdminUserOutcome.USER_NOT_FOUND, null)

        val bans = banService.getBanHistory(userId)
        val adminIds = (bans.map { it.bannedBy } + bans.mapNotNull { it.unbannedBy }).distinct()
        val adminUsernames = userService.findUsernamesByIds(adminIds)

        val banDtos = bans.map { ban ->
            AdminBanDto(
                id = ban.id,
                reason = ban.reason,
                bannedByUsername = adminUsernames[ban.bannedBy],
                bannedAt = ban.bannedAt,
                bannedUntil = ban.bannedUntil,
                unbannedByUsername = ban.unbannedBy?.let { adminUsernames[it] ?: it },
                unbannedAt = ban.unbannedAt,
                active = ban.active
            )
        }

        return Pair(CommonOutcome.SUCCESS, banDtos)
    }

    private fun buildUserStats(userId: ObjectId): AdminUserStatsDto {
        return AdminUserStatsDto(
            activeAdsCount = adRepository.countByUserIdAndStatus(userId, AdStatus.ACTIVE),
            totalAdsCount = adRepository.countByUserId(userId),
            purchasesAsBuyer = purchaseRepository.countByBuyerId(userId),
            completedPurchasesAsBuyer = purchaseRepository.countByBuyerIdAndStatus(userId, PurchaseStatus.COMPLETE),
            purchasesAsSeller = purchaseRepository.countBySellerId(userId),
            completedPurchasesAsSeller = purchaseRepository.countBySellerIdAndStatus(userId, PurchaseStatus.COMPLETE)
        )
    }

    private fun toBanDto(ban: UserBan): AdminBanDto {
        val bannedByUsername = try { userService.findById(ban.bannedBy)?.username } catch (_: Exception) { null }
        val unbannedByUsername = ban.unbannedBy?.let {
            try { userService.findById(it)?.username ?: it } catch (_: Exception) { it }
        }
        return AdminBanDto(
            id = ban.id,
            reason = ban.reason,
            bannedByUsername = bannedByUsername,
            bannedAt = ban.bannedAt,
            bannedUntil = ban.bannedUntil,
            unbannedByUsername = unbannedByUsername,
            unbannedAt = ban.unbannedAt,
            active = ban.active
        )
    }
}
