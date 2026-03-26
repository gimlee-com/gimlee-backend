package com.gimlee.auth.domain

import com.gimlee.common.domain.model.Outcome

enum class AdminUserOutcome(override val httpCode: Int) : Outcome {
    USER_BANNED_SUCCESSFULLY(200),
    USER_UNBANNED_SUCCESSFULLY(200),
    USER_ALREADY_BANNED(409),
    USER_NOT_BANNED(409),
    CANNOT_BAN_ADMIN(400),
    USER_NOT_FOUND(404);

    override val code: String get() = "ADMIN_$name"
    override val messageKey: String get() = "status.admin-user.${name.replace("_", "-").lowercase()}"
}
