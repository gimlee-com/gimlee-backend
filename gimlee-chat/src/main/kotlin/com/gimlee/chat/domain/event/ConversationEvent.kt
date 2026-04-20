package com.gimlee.chat.domain.event

import java.time.Instant

/**
 * Published when a conversation lifecycle event occurs.
 * The [cause] indicates what happened; [details] carries cause-specific payload.
 *
 * Known causes:
 * - "CREATED" → details: { "participantUserIds": [...] }
 *
 * Future causes (examples):
 * - "USER_ADDED" → details: { "addedUserIds": [...] }
 * - "USER_REMOVED" → details: { "removedUserIds": [...] }
 */
data class ConversationEvent(
    val conversationId: String,
    val type: String,
    val cause: String,
    val details: Map<String, Any> = emptyMap(),
    val linkType: String? = null,
    val linkId: String? = null,
    val timestamp: Instant = Instant.now()
)
