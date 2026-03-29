package com.gimlee.support.report.web.dto.request

import com.gimlee.support.report.domain.model.ReportReason
import com.gimlee.support.report.domain.model.ReportTargetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReportRequestDto(
    @field:NotNull val targetType: ReportTargetType,
    @field:NotBlank val targetId: String,
    @field:NotNull val reason: ReportReason,
    @field:Size(max = 2000) val description: String? = null
)
