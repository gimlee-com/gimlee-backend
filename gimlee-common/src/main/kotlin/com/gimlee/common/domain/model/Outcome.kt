package com.gimlee.common.domain.model

interface Outcome {
    val code: String
    val httpCode: Int
    val messageKey: String
}

enum class CommonOutcome(
    override val httpCode: Int,
    override val messageKey: String
) : Outcome {
    SUCCESS(200, "status.common.success"),
    INTERNAL_ERROR(500, "status.common.internal-error"),
    UNAUTHORIZED(403, "status.common.unauthorized"),
    BAD_REQUEST(400, "status.common.bad-request");

    override val code: String get() = name
}