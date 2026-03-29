package com.gimlee.support.report.domain

import com.gimlee.common.domain.model.Outcome

enum class ReportOutcome(override val httpCode: Int) : Outcome {
    REPORT_SUBMITTED(200),
    ALREADY_REPORTED(409),
    REPORT_TARGET_NOT_FOUND(404),
    REPORT_NOT_FOUND(404),
    REPORT_ASSIGNED(200),
    REPORT_STATUS_UPDATED(200),
    REPORT_RESOLVED(200),
    REPORT_NOTE_ADDED(200),
    REPORT_ALREADY_RESOLVED(409),
    REPORT_INVALID_STATUS_TRANSITION(400),
    REPORT_ASSIGNEE_NOT_FOUND(404);

    override val code: String get() = name
    override val messageKey: String get() = "status.support.${name.lowercase().replace('_', '-')}"
}
