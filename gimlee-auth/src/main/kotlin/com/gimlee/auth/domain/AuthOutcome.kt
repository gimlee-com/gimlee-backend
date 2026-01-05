package com.gimlee.auth.domain

import com.gimlee.common.domain.model.Outcome

enum class AuthOutcome(override val httpCode: Int) : Outcome {
    INCORRECT_CREDENTIALS(401),
    MISSING_CREDENTIALS(401),
    INVALID_VERIFICATION_CODE(400);

    override val code: String get() = "AUTH_$name"
    override val messageKey: String get() = "status.auth.${name.replace("_", "-").lowercase()}"
}
