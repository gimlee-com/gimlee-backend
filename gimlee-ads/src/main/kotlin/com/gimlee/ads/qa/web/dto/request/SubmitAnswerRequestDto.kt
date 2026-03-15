package com.gimlee.ads.qa.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SubmitAnswerRequestDto(
    @field:NotBlank
    @field:Size(max = 2000)
    val text: String
)
