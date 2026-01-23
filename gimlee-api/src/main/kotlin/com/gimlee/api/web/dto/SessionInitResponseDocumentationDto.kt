package com.gimlee.api.web.dto

import com.gimlee.user.web.dto.response.UserProfileDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Response for session initialization containing requested data decorators")
data class SessionInitResponseDocumentationDto(
    @Schema(description = "The JWT access token (included if 'accessToken' decorator is requested)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val accessToken: String? = null,

    @Schema(description = "The user profile (included if 'userProfile' decorator is requested)")
    val userProfile: UserProfileDto? = null,

    @Schema(description = "The user's preferred currency (included if 'preferredCurrency' decorator is requested)", example = "USD")
    val preferredCurrency: String? = null
)
