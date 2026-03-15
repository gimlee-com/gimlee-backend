package com.gimlee.ads.qa.domain

import com.gimlee.common.domain.model.Outcome

enum class QaOutcome(override val httpCode: Int) : Outcome {
    QUESTION_CREATED(201),
    QUESTION_NOT_FOUND(404),
    QUESTION_LIMIT_REACHED(429),
    QUESTION_COOLDOWN_ACTIVE(429),
    QUESTION_TOO_LONG(400),
    QUESTION_AD_NOT_FOUND(404),
    QUESTION_OWN_AD(403),
    QUESTION_AD_TOTAL_LIMIT(400),
    QUESTION_HIDDEN(200),
    QUESTION_REMOVED(200),

    ANSWER_CREATED(201),
    ANSWER_UPDATED(200),
    ANSWER_NOT_FOUND(404),
    ANSWER_ALREADY_EXISTS(409),
    ANSWER_COMMUNITY_LIMIT(429),
    ANSWER_NOT_PREVIOUS_BUYER(403),
    ANSWER_TOO_LONG(400),
    ANSWER_NOT_OWNER(403),

    UPVOTE_TOGGLED(200),

    PIN_LIMIT_REACHED(400),
    PIN_TOGGLED(200),
    NOT_AD_OWNER(403),

    ALREADY_REPORTED(409),
    REPORT_SUBMITTED(200);

    override val code: String get() = name
    override val messageKey: String get() = "status.qa.${name.lowercase().replace('_', '-')}"
}
