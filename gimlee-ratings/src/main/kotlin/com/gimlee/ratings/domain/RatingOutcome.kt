package com.gimlee.ratings.domain

import com.gimlee.common.domain.model.Outcome

enum class RatingOutcome(override val httpCode: Int) : Outcome {
    RATING_CREATED(201),
    RATING_UPDATED(200),
    RATING_DELETED(200),
    RATING_SUPPLEMENT_ADDED(200),
    RATING_RESPONSE_ADDED(200),
    ELIGIBILITY_CONSUMED(200),
    RATING_NOT_FOUND(404),
    ELIGIBILITY_NOT_FOUND(404),
    RATING_ALREADY_EXISTS(409),
    RATING_EDIT_WINDOW_CLOSED(409),
    RATING_DWELL_NOT_ELAPSED(409),
    RATING_SUPPLEMENT_TOO_SOON(409),
    RATING_SUPPLEMENT_LIMIT_REACHED(409),
    RATING_BODY_NOT_SANITIZED(400),
    RATING_INVALID_SCORE(400),
    RATING_INVALID_BODY(400),
    RATING_NOT_AUTHORIZED(403);

    override val code: String get() = name
    override val messageKey: String get() = "status.ratings.${name.replace("_", "-").lowercase()}"
}
