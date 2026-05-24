package com.gimlee.auth.domain

import com.gimlee.common.domain.model.Outcome

enum class AuthOutcome(override val httpCode: Int) : Outcome {
    INCORRECT_CREDENTIALS(401),
    MISSING_CREDENTIALS(401),
    INVALID_VERIFICATION_CODE(400),
    USER_BANNED(403),
    REFRESH_TOKEN_EXPIRED(401),
    REFRESH_TOKEN_REVOKED(401),
    REFRESH_TOKEN_REUSE_DETECTED(401),
    REFRESH_TOKEN_INVALID(401),
    REFRESH_TOKEN_DEVICE_MISMATCH(401);

    override val code: String get() = "AUTH_$name"
    override val messageKey: String get() = "status.auth.${name.replace("_", "-").lowercase()}"
}
