package com.gimlee.chat.domain

import com.gimlee.chat.domain.model.conversation.Conversation
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class ConversationTitleService(
    providers: List<ConversationTitleProvider>
) {
    private val sortedProviders = providers.sortedByDescending { it.priority }

    /**
     * Resolves titles for a list of conversations.
     * Groups conversations by the provider that can handle them and fetches titles in batch.
     */
    fun resolveTitles(
        conversations: List<Conversation>,
        currentUserId: String,
        locale: Locale
    ): Map<String, String> {
        if (conversations.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, String>()
        val unhandled = conversations.toMutableList()

        for (provider in sortedProviders) {
            val toHandle = unhandled.filter { provider.canHandle(it) }
            if (toHandle.isNotEmpty()) {
                val titles = provider.getTitles(toHandle, currentUserId, locale)
                results.putAll(titles)
                unhandled.removeAll(toHandle)
            }
            if (unhandled.isEmpty()) break
        }

        // Fill remaining with a fallback
        unhandled.forEach { 
            results[it.id] = "Conversation ${it.id}"
        }

        return results
    }
}
