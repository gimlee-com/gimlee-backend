package com.gimlee.user.web.dto.response

import com.gimlee.user.domain.model.UserProfile
import io.swagger.v3.oas.annotations.media.Schema

data class UserProfileDto(
    @field:Schema(description = "User ID")
    val userId: String,
    @field:Schema(description = "URL of the user's avatar")
    val avatarUrl: String?,
    @field:Schema(description = "Last update timestamp (epoch microseconds)")
    val updatedAt: Long
) {
    companion object {
        fun fromDomain(domain: UserProfile): UserProfileDto = UserProfileDto(
            userId = domain.userId,
            avatarUrl = domain.avatarUrl,
            updatedAt = domain.updatedAt
        )
    }
}
