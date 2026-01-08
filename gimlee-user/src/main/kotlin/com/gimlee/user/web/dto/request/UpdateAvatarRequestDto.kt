package com.gimlee.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateAvatarRequestDto(
    @field:Schema(description = "URL of the user's avatar", example = "https://example.com/avatar.png")
    @field:NotBlank(message = "Avatar URL cannot be blank.")
    val avatarUrl: String
)
