package com.gimlee.user.domain

import com.gimlee.common.domain.model.Outcome

enum class UserOutcome(override val httpCode: Int) : Outcome {
    PREFERENCES_NOT_FOUND(404),
    INVALID_USER_DATA(400),
    MAX_ADDRESSES_REACHED(400);

    override val code: String get() = "USER_$name"
    override val messageKey: String get() = "status.user.${name.replace("_", "-").lowercase()}"
}
