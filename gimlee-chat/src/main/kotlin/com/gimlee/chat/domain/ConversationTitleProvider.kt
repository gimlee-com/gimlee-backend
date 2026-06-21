package com.gimlee.chat.domain

import com.gimlee.chat.domain.model.conversation.Conversation
import java.util.Locale

/**
 * Interface for providing titles for conversations.
 * Different modules can implement this to provide titles for specific conversation types.
 */
interface ConversationTitleProvider {
    /**
     * Priority of this provider. Higher value = higher priority.
     */
    val priority: Int get() = 0

    /**
     * Whether this provider can handle the given conversation.
     */
    fun canHandle(conversation: Conversation): Boolean

    /**
     * Returns the localized titles for a list of conversations.
     * Implementation should be batched to avoid N+1 problems.
     *
     * @return Map of conversation ID to its title
     */
    fun getTitles(
        conversations: List<Conversation>,
        currentUserId: String,
        locale: Locale
    ): Map<String, String>
}
