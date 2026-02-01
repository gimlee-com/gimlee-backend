package com.gimlee.api.web.dto

import com.gimlee.user.web.dto.response.UserPresenceDto
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "User public profile with their ads")
data class UserSpaceDto(
    val user: UserSpaceDetailsDto,
    val ads: Page<AdDiscoveryPreviewDto>
)

@Schema(description = "Basic user details")
data class UserSpaceDetailsDto(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val presence: UserPresenceDto? = null
)
