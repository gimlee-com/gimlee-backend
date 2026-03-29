package com.gimlee.support.report.web.dto.request

import jakarta.validation.constraints.NotBlank

data class AssignReportRequestDto(
    @field:NotBlank val assigneeUserId: String
)
