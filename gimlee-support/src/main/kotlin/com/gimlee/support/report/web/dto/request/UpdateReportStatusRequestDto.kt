package com.gimlee.support.report.web.dto.request

import com.gimlee.support.report.domain.model.ReportStatus
import jakarta.validation.constraints.NotNull

data class UpdateReportStatusRequestDto(
    @field:NotNull val status: ReportStatus
)
