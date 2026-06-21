package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.ConversationTitleService
import com.gimlee.chat.domain.model.conversation.Conversation
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class ConversationResponseMapper(
    private val conversationTitleService: ConversationTitleService
) {
    fun toResponseDto(
        conversation: Conversation,
        currentUserId: String,
        locale: Locale
    ): ConversationResponseDto {
        val titles = conversationTitleService.resolveTitles(listOf(conversation), currentUserId, locale)
        return ConversationResponseDto.from(conversation, titles[conversation.id])
    }

    fun toResponseDtos(
        conversations: List<Conversation>,
        currentUserId: String,
        locale: Locale
    ): List<ConversationResponseDto> {
        val titles = conversationTitleService.resolveTitles(conversations, currentUserId, locale)
        return conversations.map { ConversationResponseDto.from(it, titles[it.id]) }
    }

    fun toListResponseDto(
        conversations: List<Conversation>,
        hasMore: Boolean,
        currentUserId: String,
        locale: Locale
    ): ConversationListResponseDto {
        return ConversationListResponseDto(
            hasMore = hasMore,
            conversations = toResponseDtos(conversations, currentUserId, locale)
        )
    }
}
