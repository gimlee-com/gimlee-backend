package com.gimlee.notifications.domain

import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import com.gimlee.notifications.domain.model.*
import com.gimlee.notifications.persistence.NotificationRepository
import com.gimlee.notifications.persistence.model.NotificationDocument
import com.gimlee.notifications.sse.NotificationSseBroadcaster
import com.gimlee.notifications.sse.NotificationSseEvent
import com.gimlee.notifications.web.dto.NotificationDto
import com.gimlee.notifications.web.dto.NotificationListDto
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Locale

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val sseBroadcaster: NotificationSseBroadcaster,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createNotification(
        userId: String,
        type: NotificationType,
        language: String,
        titleArgs: Array<Any>? = null,
        messageArgs: Array<Any>? = null,
        suggestedAction: SuggestedAction? = null,
        metadata: Map<String, String>? = null
    ): Notification {
        val locale = Locale.forLanguageTag(language)
        val title = messageSource.getMessage(type.titleKey, titleArgs, locale)
        val message = messageSource.getMessage(type.messageKeyTemplate, messageArgs, locale)
        val now = Instant.now().toMicros()
        val id = UUIDv7.generate().toString()

        val doc = NotificationDocument(
            id = id,
            userId = ObjectId(userId),
            type = type.slug,
            category = type.category.shortName,
            severity = type.defaultSeverity.shortName,
            title = title,
            message = message,
            read = false,
            suggestedAction = suggestedAction,
            metadata = metadata,
            createdAt = now
        )

        notificationRepository.save(doc)
        val notification = doc.toDomain()

        sseBroadcaster.broadcast(userId, NotificationSseEvent.Created(notification))
        broadcastUnreadCount(userId)

        log.debug("Notification created: type={}, userId={}, id={}", type.slug, userId, id)
        return notification
    }

    fun getNotifications(userId: String, category: String?, limit: Int, beforeId: String?): NotificationListDto {
        val userOid = ObjectId(userId)
        val catShortName = category?.let { NotificationCategory.valueOf(it.uppercase()).shortName }
        val fetchLimit = limit + 1

        val docs = notificationRepository.findByUserId(userOid, catShortName, fetchLimit, beforeId)
        val hasMore = docs.size > limit
        val results = docs.take(limit)
        val unreadCount = notificationRepository.countUnread(userOid)

        return NotificationListDto(
            notifications = results.map { NotificationDto.from(it.toDomain()) },
            hasMore = hasMore,
            unreadCount = unreadCount
        )
    }

    fun markAsRead(notificationId: String, userId: String): NotificationOutcome {
        val doc = notificationRepository.findById(notificationId)
            ?: return NotificationOutcome.NOTIFICATION_NOT_FOUND
        if (doc.userId.toHexString() != userId) {
            return NotificationOutcome.NOTIFICATION_ACCESS_DENIED
        }

        notificationRepository.markAsRead(notificationId, ObjectId(userId))
        sseBroadcaster.broadcast(userId, NotificationSseEvent.Read(notificationId))
        broadcastUnreadCount(userId)

        return NotificationOutcome.NOTIFICATION_MARKED_READ
    }

    fun markAllAsRead(userId: String, category: String?): NotificationOutcome {
        val catShortName = category?.let { NotificationCategory.valueOf(it.uppercase()).shortName }
        notificationRepository.markAllAsRead(ObjectId(userId), catShortName)

        sseBroadcaster.broadcast(userId, NotificationSseEvent.AllRead(category))
        broadcastUnreadCount(userId)

        return NotificationOutcome.NOTIFICATIONS_MARKED_READ
    }

    fun getUnreadCount(userId: String): Long {
        return notificationRepository.countUnread(ObjectId(userId))
    }

    private fun broadcastUnreadCount(userId: String) {
        val count = notificationRepository.countUnread(ObjectId(userId))
        sseBroadcaster.broadcast(userId, NotificationSseEvent.UnreadCountChanged(count))
    }
}
