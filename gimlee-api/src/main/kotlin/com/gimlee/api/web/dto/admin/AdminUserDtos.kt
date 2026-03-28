package com.gimlee.api.web.dto.admin

import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.model.Role
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "User summary for admin list view")
data class AdminUserListItemDto(
    @Schema(description = "User ID") val userId: String,
    @Schema(description = "Username") val username: String?,
    @Schema(description = "Display name") val displayName: String?,
    @Schema(description = "Email address") val email: String?,
    @Schema(description = "Account status") val status: UserStatus?,
    @Schema(description = "Assigned roles") val roles: List<Role>,
    @Schema(description = "Last login timestamp") val lastLogin: Instant?,
    @Schema(description = "Avatar URL") val avatarUrl: String?
)

@Schema(description = "Detailed user information for admin view")
data class AdminUserDetailDto(
    @Schema(description = "User ID") val userId: String,
    @Schema(description = "Username") val username: String?,
    @Schema(description = "Display name") val displayName: String?,
    @Schema(description = "Email address") val email: String?,
    @Schema(description = "Phone number") val phone: String?,
    @Schema(description = "Account status") val status: UserStatus?,
    @Schema(description = "Assigned roles") val roles: List<Role>,
    @Schema(description = "Last login timestamp") val lastLogin: Instant?,
    @Schema(description = "Avatar URL") val avatarUrl: String?,
    @Schema(description = "Preferred language") val language: String?,
    @Schema(description = "Preferred currency") val preferredCurrency: String?,
    @Schema(description = "Last seen timestamp (epoch micros)") val lastSeenAt: Long?,
    @Schema(description = "User activity stats") val stats: AdminUserStatsDto,
    @Schema(description = "Active ban details, if currently banned") val activeBan: AdminBanDto?
)

@Schema(description = "Aggregated user statistics")
data class AdminUserStatsDto(
    @Schema(description = "Number of active ads") val activeAdsCount: Long,
    @Schema(description = "Total number of ads (all statuses)") val totalAdsCount: Long,
    @Schema(description = "Total purchases as buyer") val purchasesAsBuyer: Long,
    @Schema(description = "Completed purchases as buyer") val completedPurchasesAsBuyer: Long,
    @Schema(description = "Total purchases as seller") val purchasesAsSeller: Long,
    @Schema(description = "Completed purchases as seller") val completedPurchasesAsSeller: Long
)

@Schema(description = "Ban record")
data class AdminBanDto(
    @Schema(description = "Ban record ID") val id: String,
    @Schema(description = "Ban reason (free text)") val reason: String,
    @Schema(description = "Username of the admin who issued the ban") val bannedByUsername: String?,
    @Schema(description = "When the ban was issued (epoch micros)") val bannedAt: Long,
    @Schema(description = "When the ban expires (epoch micros), null if permanent") val bannedUntil: Long?,
    @Schema(description = "Username of the admin who lifted the ban") val unbannedByUsername: String?,
    @Schema(description = "When the ban was lifted (epoch micros)") val unbannedAt: Long?,
    @Schema(description = "Whether this ban is currently active") val active: Boolean
)
