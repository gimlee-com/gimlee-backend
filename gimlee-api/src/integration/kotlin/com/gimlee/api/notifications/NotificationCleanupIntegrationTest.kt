package com.gimlee.api.notifications

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import com.gimlee.notifications.domain.NotificationCleanupJob
import com.gimlee.notifications.domain.model.NotificationType
import com.gimlee.notifications.persistence.NotificationRepository
import com.gimlee.notifications.persistence.model.NotificationDocument
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.time.temporal.ChronoUnit

@Import(NotificationTestConfig::class)
class NotificationCleanupIntegrationTest(
    private val notificationRepository: NotificationRepository,
    private val notificationCleanupJob: NotificationCleanupJob
) : BaseIntegrationTest({

    fun seedNotification(userId: ObjectId, createdAt: Long): NotificationDocument {
        val doc = NotificationDocument(
            id = UUIDv7.generate().toString(),
            userId = userId,
            type = NotificationType.ORDER_NEW.slug,
            category = NotificationType.ORDER_NEW.category.shortName,
            severity = NotificationType.ORDER_NEW.defaultSeverity.shortName,
            title = "Test",
            message = "Test message",
            read = false,
            actionUrl = null,
            metadata = null,
            createdAt = createdAt
        )
        notificationRepository.save(doc)
        return doc
    }

    beforeSpec {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
    }

    Given("notifications of different ages") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val now = Instant.now()

        val oldNotification = seedNotification(
            userId, now.minus(91, ChronoUnit.DAYS).toMicros()
        )
        val recentNotification = seedNotification(
            userId, now.minus(89, ChronoUnit.DAYS).toMicros()
        )
        val todayNotification = seedNotification(
            userId, now.toMicros()
        )

        When("cleanup job runs") {
            notificationCleanupJob.cleanupOldNotifications()

            Then("notifications older than retention period should be deleted") {
                notificationRepository.findById(oldNotification.id) shouldBe null
            }

            Then("recent notifications should be retained") {
                notificationRepository.findById(recentNotification.id) shouldBe recentNotification
            }

            Then("today's notifications should be retained") {
                notificationRepository.findById(todayNotification.id) shouldBe todayNotification
            }
        }
    }

    Given("no old notifications") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val now = Instant.now()

        seedNotification(userId, now.toMicros())
        seedNotification(userId, now.minus(1, ChronoUnit.DAYS).toMicros())

        When("cleanup job runs") {
            notificationCleanupJob.cleanupOldNotifications()

            Then("no notifications should be deleted") {
                notificationRepository.findByUserId(userId, null, 100, null).size shouldBe 2
            }
        }
    }
})
