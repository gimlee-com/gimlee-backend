package com.gimlee.ads.qa.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AskQuestionRequestDto(
    @field:NotBlank
    @field:Size(max = 500)
    val text: String
)
