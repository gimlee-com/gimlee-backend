package com.gimlee.user.web.dto.request

import com.gimlee.user.domain.model.UserPresenceStatus
import jakarta.validation.constraints.NotNull

data class UpdateUserPresenceRequestDto(
    @field:NotNull
    val status: UserPresenceStatus,
    val customStatus: String? = null
)
