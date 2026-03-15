package com.gimlee.reports.domain

import com.gimlee.common.domain.model.Outcome

enum class ReportOutcome(override val httpCode: Int) : Outcome {
    REPORT_SUBMITTED(200),
    ALREADY_REPORTED(409),
    REPORT_TARGET_NOT_FOUND(404);

    override val code: String get() = name
    override val messageKey: String get() = "status.reports.${name.lowercase().replace('_', '-')}"
}
