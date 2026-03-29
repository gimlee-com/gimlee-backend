package com.gimlee.support.report.web.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddReportNoteRequestDto(
    @field:NotBlank @field:Size(max = 5000) val note: String
)
