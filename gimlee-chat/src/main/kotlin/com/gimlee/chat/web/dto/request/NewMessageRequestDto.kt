package com.gimlee.chat.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class NewMessageRequestDto(
    @field:NotBlank
    @field:Size(max = 2000)
    val message: String
)
