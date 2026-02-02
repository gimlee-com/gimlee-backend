package com.gimlee.chat.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ChatOutcome
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
    private val messageSource: MessageSource,
    private val chatEventBroadcaster: ChatEventBroadcaster
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Send Message",
        description = "Sends a new message to the specified chat. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Message sent successfully")
    @PostMapping("/{chatId}/messages")
    @Privileged(role = "USER")
    fun sendMessage(
        @PathVariable chatId: String,
        @Valid @RequestBody request: NewMessageRequestDto
    ): ResponseEntity<Any> {
        val username = HttpServletRequestAuthUtil.getPrincipal().username
        chatService.sendMessage(chatId, username, request.message)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Indicate Typing",
        description = "Broadcasts a typing indicator to other participants. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Typing indicator broadcasted")
    @PostMapping("/{chatId}/typing")
    @Privileged(role = "USER")
    fun indicateTyping(@PathVariable chatId: String): ResponseEntity<Any> {
        val username = HttpServletRequestAuthUtil.getPrincipal().username
        chatService.indicateTyping(chatId, username)
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Touch Chat",
        description = "Ensures the chat is initialized. Primarily used for performance testing. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Chat touched")
    @PostMapping("/{chatId}/touch")
    @Privileged(role = "USER")
    fun touchChat(@PathVariable chatId: String): ResponseEntity<Any> {
        // In this implementation, we don't need to do anything specific to initialize a chat
        return handleOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(
        summary = "Get History",
        description = "Retrieves archived messages for the specified chat. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @GetMapping("/{chatId}/messages")
    @Privileged(role = "USER")
    fun getHistory(
        @PathVariable chatId: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) beforeId: String?
    ): ResponseEntity<Any> {
        val messages = chatService.getHistory(chatId, limit, beforeId)
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
    @GetMapping(value = ["/{chatId}/events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Privileged(role = "USER")
    fun getEventsStream(@PathVariable chatId: String): SseEmitter {
        return chatEventBroadcaster.createEmitter(chatId)
    }
}
