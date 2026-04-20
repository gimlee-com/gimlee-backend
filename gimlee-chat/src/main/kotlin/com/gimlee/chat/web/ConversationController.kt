package com.gimlee.chat.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.ChatOutcome
import com.gimlee.chat.domain.model.ChatPrincipalProvider
import com.gimlee.chat.web.dto.response.ConversationListResponseDto
import com.gimlee.chat.web.dto.response.ConversationResponseDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Tag(name = "Conversations", description = "Endpoints for conversation management")
@RestController
@RequestMapping("/conversations")
class ConversationController(
    private val conversationService: ConversationService,
    private val principalProvider: ChatPrincipalProvider,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Get Conversation",
        description = "Retrieves details of a specific conversation. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Conversation retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @ApiResponse(responseCode = "403", description = "Not a participant")
    @GetMapping("/{conversationId}")
    @Privileged(role = "USER")
    fun getConversation(@PathVariable conversationId: String): ResponseEntity<Any> {
        val userId = principalProvider.getUserId()

        val accessOutcome = conversationService.verifyReadAccess(conversationId, userId)
        if (accessOutcome != null) return handleOutcome(accessOutcome)

        val conversation = conversationService.findById(conversationId)!!
        return handleOutcome(CommonOutcome.SUCCESS, ConversationResponseDto.from(conversation))
    }

    @Operation(
        summary = "List Conversations",
        description = "Lists the authenticated user's conversations, ordered by last activity. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Conversations retrieved successfully")
    @GetMapping
    @Privileged(role = "USER")
    fun listConversations(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) beforeActivityAt: Instant?
    ): ResponseEntity<Any> {
        val userId = principalProvider.getUserId()
        val beforeMicros = beforeActivityAt?.toMicros()

        val conversations = conversationService.findByParticipant(userId, limit, beforeMicros)
        val response = ConversationListResponseDto(
            hasMore = conversations.size >= limit,
            conversations = conversations.map { ConversationResponseDto.from(it) }
        )
        return handleOutcome(CommonOutcome.SUCCESS, response)
    }
}
