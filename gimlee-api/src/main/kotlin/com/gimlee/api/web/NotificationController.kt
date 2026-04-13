package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.sse.NotificationSseBroadcaster
import com.gimlee.notifications.web.dto.NotificationListDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Notifications", description = "In-app notification endpoints")
@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val sseBroadcaster: NotificationSseBroadcaster,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List notifications",
        description = "Returns paginated notifications for the authenticated user. Supports cursor-based pagination and optional category filtering."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Notifications retrieved successfully",
        content = [Content(schema = Schema(implementation = NotificationListDto::class))]
    )
    @GetMapping
    @Privileged(role = "USER")
    fun listNotifications(
        @Parameter(description = "Filter by category (orders, messages, ads, qa, support, account)")
        @RequestParam(required = false) category: String?,
        @Parameter(description = "Number of notifications to return (default 20, max 50)")
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(description = "Return notifications before this notification ID (cursor-based pagination)")
        @RequestParam(required = false) beforeId: String?
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val clampedLimit = limit.coerceIn(1, 50)
        val result = notificationService.getNotifications(userId, category, clampedLimit, beforeId)
        return handleOutcome(CommonOutcome.SUCCESS, result)
    }

    @Operation(
        summary = "Get unread count",
        description = "Returns the total number of unread notifications for the authenticated user."
    )
    @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully")
    @GetMapping("/unread-count")
    @Privileged(role = "USER")
    fun getUnreadCount(): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val count = notificationService.getUnreadCount(userId)
        return handleOutcome(CommonOutcome.SUCCESS, mapOf("count" to count))
    }

    @Operation(
        summary = "Mark notification as read",
        description = "Marks a single notification as read. Only the notification owner can mark it."
    )
    @ApiResponse(responseCode = "200", description = "Notification marked as read")
    @PatchMapping("/{id}/read")
    @Privileged(role = "USER")
    fun markAsRead(
        @Parameter(description = "Notification ID") @PathVariable id: String
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = notificationService.markAsRead(id, userId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Mark all notifications as read",
        description = "Marks all unread notifications as read. Optionally filter by category."
    )
    @ApiResponse(responseCode = "200", description = "All notifications marked as read")
    @PatchMapping("/read-all")
    @Privileged(role = "USER")
    fun markAllAsRead(
        @Parameter(description = "Optional category filter")
        @RequestParam(required = false) category: String?
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = notificationService.markAllAsRead(userId, category)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Notification event stream",
        description = "Opens a Server-Sent Events (SSE) stream for real-time notification delivery. " +
                "Events include: 'notification' (new), 'notification-read', 'notifications-all-read', 'unread-count'."
    )
    @GetMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Privileged(role = "USER")
    fun getNotificationStream(): SseEmitter {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        return sseBroadcaster.createEmitter(userId)
    }
}
