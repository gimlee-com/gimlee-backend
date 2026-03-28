package com.gimlee.api.web.dto.admin

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Request to ban a user")
data class BanUserRequestDto(
    @field:NotBlank
    @field:Size(min = 5, max = 2000)
    @Schema(description = "Reason for banning the user (5–2000 characters)", example = "Repeated policy violations")
    val reason: String,

    @Schema(description = "When the ban expires (epoch micros). Null means permanent ban.", example = "null")
    val bannedUntil: Long? = null
)
