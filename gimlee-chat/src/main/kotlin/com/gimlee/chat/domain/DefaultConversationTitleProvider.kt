package com.gimlee.chat.domain

import com.gimlee.auth.service.UserService
import com.gimlee.chat.domain.model.conversation.Conversation
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Default provider that generates a title based on participant names.
 * E.g., "Conversation with Alice, Bob"
 */
@Component
class DefaultConversationTitleProvider(
    private val userService: UserService,
    private val messageSource: MessageSource
) : ConversationTitleProvider {

    companion object {
        private const val MAX_NAMES_IN_TITLE = 3
    }

    override val priority: Int = -100 // Lowest priority

    override fun canHandle(conversation: Conversation): Boolean = true

    override fun getTitles(
        conversations: List<Conversation>,
        currentUserId: String,
        locale: Locale
    ): Map<String, String> {
        // Collect all user IDs to fetch in batch
        val allOtherUserIds = conversations.flatMap { conv ->
            conv.participants.map { it.userId }.filter { it != currentUserId }
        }.distinct()

        val usernames = userService.findUsernamesByIds(allOtherUserIds)

        return conversations.associate { conv ->
            val otherParticipants = conv.participants
                .map { it.userId }
                .filter { it != currentUserId }
            
            val displayNames = otherParticipants.map { usernames[it] ?: "Unknown" }
            
            val title = when {
                displayNames.isEmpty() -> {
                    messageSource.getMessage("gimlee.chat.conversation.default.empty", null, locale)
                }
                displayNames.size > MAX_NAMES_IN_TITLE -> {
                    val firstNames = displayNames.take(MAX_NAMES_IN_TITLE).joinToString(", ")
                    val remainingCount = displayNames.size - MAX_NAMES_IN_TITLE
                    messageSource.getMessage(
                        "gimlee.chat.conversation.default.with-names-truncated",
                        arrayOf(firstNames, remainingCount),
                        locale
                    )
                }
                else -> {
                    val names = displayNames.joinToString(", ")
                    messageSource.getMessage("gimlee.chat.conversation.default.with-names", arrayOf(names), locale)
                }
            }
            
            conv.id to title
        }
    }
}
