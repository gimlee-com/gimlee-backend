package com.gimlee.ads.qa.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class QaReportRequestDto(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)
