package com.gimlee.api.chat

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.ChatOutcome
import com.gimlee.chat.web.dto.response.ConversationResponseDto
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Order Conversation", description = "Endpoints for order-specific conversation management")
@RestController
@RequestMapping("/purchases")
class OrderConversationFacadeController(
    private val conversationService: ConversationService,
    private val chatService: ChatService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Get Order Conversation",
        description = "Retrieves the conversation associated with a purchase, including recent messages. Requires USER role."
    )
    @ApiResponse(responseCode = "200", description = "Conversation retrieved successfully")
    @ApiResponse(responseCode = "404", description = "No conversation found for this purchase")
    @ApiResponse(responseCode = "403", description = "Not a participant of this conversation")
    @GetMapping("/{purchaseId}/conversation")
    @Privileged(role = "USER")
    fun getOrderConversation(
        @PathVariable purchaseId: String,
        @RequestParam(defaultValue = "20") recentMessagesLimit: Int
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId

        val conversation = conversationService.findByLink(ConversationLinkTypes.PURCHASE, purchaseId)
            ?: return handleOutcome(ChatOutcome.CONVERSATION_NOT_FOUND)

        if (!conversation.isParticipant(userId)) {
            return handleOutcome(ChatOutcome.NOT_A_PARTICIPANT)
        }

        val recentMessages = chatService.getHistory(conversation.id, recentMessagesLimit, null)

        val response = mapOf(
            "conversation" to ConversationResponseDto.from(conversation),
            "recentMessages" to recentMessages
        )

        return handleOutcome(CommonOutcome.SUCCESS, response)
    }
}
