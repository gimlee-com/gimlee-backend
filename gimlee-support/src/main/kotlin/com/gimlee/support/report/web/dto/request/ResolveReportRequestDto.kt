package com.gimlee.support.report.web.dto.request

import com.gimlee.support.report.domain.model.ReportResolution
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ResolveReportRequestDto(
    @field:NotNull val resolution: ReportResolution,
    @field:Size(max = 5000) val internalNotes: String? = null
)
