package com.gimlee.auth.web.dto.request

import jakarta.validation.constraints.Size

data class ChangePasswordRequestDto(
    @get:Size(min = 8, max = 64)
    val oldPassword: String,

    @get:Size(min = 8, max = 64)
    val newPassword: String
)