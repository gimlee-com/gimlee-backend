package com.gimlee.reports.web.dto.request

import com.gimlee.reports.domain.model.ReportTargetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReportRequestDto(
    @field:NotNull
    val targetType: ReportTargetType,

    @field:NotBlank
    val targetId: String,

    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)
