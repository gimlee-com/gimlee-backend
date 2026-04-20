package com.gimlee.chat.domain

import com.gimlee.common.domain.model.Outcome

enum class ChatOutcome(override val httpCode: Int) : Outcome {
    CHAT_NOT_FOUND(404),
    INVALID_MESSAGE(400),
    MESSAGE_TOO_LONG(400),
    UNAUTHORIZED_ACCESS(403),
    CONVERSATION_NOT_FOUND(404),
    CONVERSATION_LOCKED(403),
    CONVERSATION_ARCHIVED(403),
    NOT_A_PARTICIPANT(403),
    CONVERSATION_ALREADY_EXISTS(409),
    MAX_PARTICIPANTS_EXCEEDED(400);

    override val code: String get() = "CHAT_$name"
    override val messageKey: String get() = "status.chat.${name.replace("_", "-").lowercase()}"
}
