package com.gimlee.chat.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ChatOutcome
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.ChatPrincipalProvider
import com.gimlee.chat.web.dto.request.NewMessageRequestDto
import com.gimlee.chat.web.dto.response.ArchivedMessagesResponseDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Chat", description = "Endpoints for chat functionality")
@RestController
@RequestMapping("/chat")
class ChatController(
    private val chatService: ChatService,
    private val conversationService: ConversationService,
    private val principalProvider: ChatPrincipalProvider,
    private val messageSource: MessageSource,
    private val chatEventBroadcaster: ChatEventBroadcaster
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Send Message",
        description = "Sends a new message to the specified conversation. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Message sent successfully")
    @ApiResponse(responseCode = "403", description = "Not a participant or conversation is locked")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @PostMapping("/{conversationId}/messages")
    @Privileged(role = "USER")
    fun sendMessage(
        @PathVariable conversationId: String,
        @Valid @RequestBody request: NewMessageRequestDto
    ): ResponseEntity<Any> {
        val userId = principalProvider.getUserId()
        val username = principalProvider.getUsername()

        val accessOutcome = conversationService.verifyWriteAccess(conversationId, userId)
        if (accessOutcome != null) return handleOutcome(accessOutcome)

        chatService.sendMessage(conversationId, userId, username, request.message)
        conversationService.updateLastActivity(conversationId)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Indicate Typing",
        description = "Broadcasts a typing indicator to other participants. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Typing indicator broadcasted")
    @PostMapping("/{conversationId}/typing")
    @Privileged(role = "USER")
    fun indicateTyping(@PathVariable conversationId: String): ResponseEntity<Any> {
        val userId = principalProvider.getUserId()
        val username = principalProvider.getUsername()

        val accessOutcome = conversationService.verifyWriteAccess(conversationId, userId)
        if (accessOutcome != null) return handleOutcome(accessOutcome)

        chatService.indicateTyping(conversationId, userId, username)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Get History",
        description = "Retrieves archived messages for the specified conversation. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Not a participant or conversation is archived")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @GetMapping("/{conversationId}/messages")
    @Privileged(role = "USER")
    fun getHistory(
        @PathVariable conversationId: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) beforeId: String?
    ): ResponseEntity<Any> {
        val userId = principalProvider.getUserId()

        val accessOutcome = conversationService.verifyReadAccess(conversationId, userId)
        if (accessOutcome != null) return handleOutcome(accessOutcome)

        val messages = chatService.getHistory(conversationId, limit, beforeId)
        val response = ArchivedMessagesResponseDto(
            hasMore = messages.size >= limit,
            messages = messages
        )
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "Chat Events Stream",
        description = "Exposes a Server-Sent Events (SSE) stream for real-time chat updates. Requires USER role."
    )
    @GetMapping(value = ["/{conversationId}/events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Privileged(role = "USER")
    fun getEventsStream(@PathVariable conversationId: String): SseEmitter {
        val userId = principalProvider.getUserId()

        val accessOutcome = conversationService.verifyReadAccess(conversationId, userId)
        if (accessOutcome != null) {
            val emitter = SseEmitter(0L)
            emitter.completeWithError(IllegalAccessException("Access denied"))
            return emitter
        }

        return chatEventBroadcaster.createEmitter(conversationId, userId)
    }
}
