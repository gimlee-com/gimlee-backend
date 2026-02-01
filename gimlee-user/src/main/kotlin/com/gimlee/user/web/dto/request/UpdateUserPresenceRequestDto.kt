package com.gimlee.user.web.dto.request

import com.gimlee.user.domain.model.UserPresenceStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateUserPresenceRequestDto(
    @field:NotNull
    val status: UserPresenceStatus,
    @field:Size(max = 100)
    val customStatus: String? = null
)
