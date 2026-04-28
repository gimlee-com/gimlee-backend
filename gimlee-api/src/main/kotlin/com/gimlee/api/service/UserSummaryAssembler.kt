package com.gimlee.api.service

import com.gimlee.api.web.dto.UserSummaryDto
import com.gimlee.auth.service.UserService
import com.gimlee.user.domain.ProfileService
import org.springframework.stereotype.Service

@Service
class UserSummaryAssembler(
    private val userService: UserService,
    private val profileService: ProfileService
) {
    fun assemble(userIds: List<String>): Map<String, UserSummaryDto> {
        if (userIds.isEmpty()) return emptyMap()
        val distinctIds = userIds.distinct()
        val usernames = userService.findUsernamesByIds(distinctIds)
        val avatars = profileService.getAvatarsByUserIds(distinctIds)
        return distinctIds.associateWith { id ->
            UserSummaryDto(
                username = usernames[id] ?: "Unknown",
                avatarUrl = avatars[id]
            )
        }
    }
}
