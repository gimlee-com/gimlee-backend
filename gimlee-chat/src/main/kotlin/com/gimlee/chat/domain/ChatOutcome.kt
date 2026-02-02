package com.gimlee.chat.domain

import com.gimlee.common.domain.model.Outcome

enum class ChatOutcome(override val httpCode: Int) : Outcome {
    CHAT_NOT_FOUND(404),
    INVALID_MESSAGE(400),
    MESSAGE_TOO_LONG(400),
    UNAUTHORIZED_ACCESS(403);

    override val code: String get() = "CHAT_$name"
    override val messageKey: String get() = "status.chat.${name.replace("_", "-").lowercase()}"
}
