package com.gimlee.api.notifications

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import com.gimlee.notifications.domain.model.NotificationCategory
import com.gimlee.notifications.domain.model.NotificationSeverity
import com.gimlee.notifications.domain.model.NotificationType
import com.gimlee.notifications.persistence.NotificationRepository
import com.gimlee.notifications.persistence.model.NotificationDocument
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.time.temporal.ChronoUnit

@Import(NotificationTestConfig::class)
class NotificationControllerIntegrationTest(
    private val notificationRepository: NotificationRepository
) : BaseIntegrationTest({

    fun userHeaders(userId: ObjectId): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = listOf("USER")
        )

    fun seedNotification(
        userId: ObjectId,
        type: NotificationType = NotificationType.ORDER_NEW,
        read: Boolean = false,
        createdAt: Long = Instant.now().toMicros(),
        actionUrl: String? = null,
        metadata: Map<String, String>? = null
    ): NotificationDocument {
        val doc = NotificationDocument(
            id = UUIDv7.generate().toString(),
            userId = userId,
            type = type.slug,
            category = type.category.shortName,
            severity = type.defaultSeverity.shortName,
            title = "Test Title",
            message = "Test Message",
            read = read,
            actionUrl = actionUrl,
            metadata = metadata,
            createdAt = createdAt
        )
        notificationRepository.save(doc)
        return doc
    }

    beforeSpec {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
    }

    // ===============================================================
    // 1. Authentication
    // ===============================================================
    Given("unauthenticated requests") {
        When("listing notifications without auth") {
            val response = restClient.get("/notifications")
            Then("should return 401") {
                response.statusCode shouldBe 401
            }
        }
        When("getting unread count without auth") {
            val response = restClient.get("/notifications/unread-count")
            Then("should return 401") {
                response.statusCode shouldBe 401
            }
        }
        When("marking notification as read without auth") {
            val response = restClient.patch("/notifications/some-id/read")
            Then("should return 401") {
                response.statusCode shouldBe 401
            }
        }
        When("marking all as read without auth") {
            val response = restClient.patch("/notifications/read-all")
            Then("should return 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // ===============================================================
    // 2. List notifications
    // ===============================================================
    Given("a user with notifications") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)
        val now = Instant.now()

        val n1 = seedNotification(userId, NotificationType.ORDER_NEW, createdAt = now.minus(3, ChronoUnit.HOURS).toMicros())
        val n2 = seedNotification(userId, NotificationType.ORDER_COMPLETE, createdAt = now.minus(2, ChronoUnit.HOURS).toMicros())
        val n3 = seedNotification(userId, NotificationType.AD_STOCK_DEPLETED, createdAt = now.minus(1, ChronoUnit.HOURS).toMicros())
        val n4 = seedNotification(userId, NotificationType.QA_NEW_QUESTION, read = true, createdAt = now.toMicros())

        When("listing all notifications") {
            val response = restClient.get("/notifications", headers)
            Then("should return 200 with all notifications") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 4
                (data["unreadCount"] as Number).toLong() shouldBe 3
            }
        }

        When("listing notifications filtered by ORDERS category") {
            val response = restClient.get("/notifications?category=orders", headers)
            Then("should return only order notifications") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 2
            }
        }

        When("listing notifications filtered by ADS category") {
            val response = restClient.get("/notifications?category=ads", headers)
            Then("should return only ad notifications") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 1
                val notif = notifications[0] as Map<*, *>
                notif["type"] shouldBe "ad.stock_depleted"
            }
        }

        When("listing notifications with invalid category") {
            val response = restClient.get("/notifications?category=invalid", headers)
            Then("should return error") {
                response.statusCode shouldBe 400
            }
        }
    }

    // ===============================================================
    // 3. Cursor-based pagination
    // ===============================================================
    Given("a user with many notifications for pagination") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)
        val base = Instant.now()

        val notifications = (1..5).map { i ->
            seedNotification(userId, NotificationType.ORDER_NEW, createdAt = base.minus((6L - i), ChronoUnit.HOURS).toMicros())
        }

        When("listing with limit=2") {
            val response = restClient.get("/notifications?limit=2", headers)
            Then("should return 2 notifications with hasMore=true") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val items = data["notifications"] as List<*>
                items shouldHaveSize 2
                data["hasMore"] shouldBe true
            }
        }

        When("listing page 2 using cursor from page 1") {
            val page1 = restClient.get("/notifications?limit=2", headers)
            val page1Data = (page1.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
            val page1Items = page1Data["notifications"] as List<*>
            val lastNotifId = (page1Items[1] as Map<*, *>)["id"] as String

            val page2 = restClient.get("/notifications?limit=2&beforeId=$lastNotifId", headers)
            Then("should return next 2 notifications") {
                page2.statusCode shouldBe 200
                val page2Body = page2.bodyAs<Map<String, Any>>()!!
                val page2Data = page2Body["data"] as Map<*, *>
                val page2Items = page2Data["notifications"] as List<*>
                page2Items shouldHaveSize 2
                page2Data["hasMore"] shouldBe true
            }
        }

        When("listing with nonexistent beforeId") {
            val response = restClient.get("/notifications?limit=10&beforeId=nonexistent-id-123", headers)
            Then("should return all notifications (cursor not found, ignored)") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val items = data["notifications"] as List<*>
                items shouldHaveSize 5
            }
        }
    }

    // ===============================================================
    // 4. Empty state
    // ===============================================================
    Given("a user with no notifications") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        When("listing notifications") {
            val response = restClient.get("/notifications", headers)
            Then("should return empty list") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 0
                data["hasMore"] shouldBe false
                (data["unreadCount"] as Number).toLong() shouldBe 0
            }
        }
    }

    // ===============================================================
    // 5. Unread count
    // ===============================================================
    Given("a user with mixed read/unread notifications") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        seedNotification(userId, NotificationType.ORDER_NEW, read = false)
        seedNotification(userId, NotificationType.ORDER_COMPLETE, read = false)
        seedNotification(userId, NotificationType.ORDER_CANCELLED, read = true)

        When("getting unread count") {
            val response = restClient.get("/notifications/unread-count", headers)
            Then("should return count of 2") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                (data["count"] as Number).toLong() shouldBe 2
            }
        }
    }

    // ===============================================================
    // 6. Mark as read
    // ===============================================================
    Given("a user marking notifications as read") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        val notification = seedNotification(userId, NotificationType.ORDER_NEW, read = false)

        When("marking own notification as read") {
            val response = restClient.patch("/notifications/${notification.id}/read", headers = headers)
            Then("should return 200") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
            }
            Then("notification should be marked as read in DB") {
                val doc = notificationRepository.findById(notification.id)!!
                doc.read shouldBe true
            }
        }

        When("marking already-read notification as read again (idempotent)") {
            val response = restClient.patch("/notifications/${notification.id}/read", headers = headers)
            Then("should still return 200") {
                response.statusCode shouldBe 200
            }
        }

        When("marking nonexistent notification as read") {
            val response = restClient.patch("/notifications/nonexistent-id/read", headers = headers)
            Then("should return 404") {
                response.statusCode shouldBe 404
            }
        }

        When("marking another user's notification as read") {
            val otherUserId = ObjectId.get()
            val otherNotification = seedNotification(otherUserId, NotificationType.ORDER_NEW, read = false)
            val response = restClient.patch("/notifications/${otherNotification.id}/read", headers = headers)
            Then("should return 403") {
                response.statusCode shouldBe 403
            }
        }
    }

    // ===============================================================
    // 7. Mark all as read
    // ===============================================================
    Given("a user marking all notifications as read") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        seedNotification(userId, NotificationType.ORDER_NEW, read = false)
        seedNotification(userId, NotificationType.ORDER_COMPLETE, read = false)
        seedNotification(userId, NotificationType.AD_STOCK_DEPLETED, read = false)

        When("marking all as read without category") {
            val response = restClient.patch("/notifications/read-all", headers = headers)
            Then("should return 200 and mark all as read") {
                response.statusCode shouldBe 200
                notificationRepository.countUnread(userId) shouldBe 0
            }
        }
    }

    Given("a user marking all as read with category filter") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        seedNotification(userId, NotificationType.ORDER_NEW, read = false)
        seedNotification(userId, NotificationType.ORDER_COMPLETE, read = false)
        seedNotification(userId, NotificationType.AD_STOCK_DEPLETED, read = false)

        When("marking all ORDERS as read") {
            val response = restClient.patch("/notifications/read-all?category=orders", headers = headers)
            Then("should mark only order notifications as read") {
                response.statusCode shouldBe 200
                notificationRepository.countUnread(userId) shouldBe 1
            }
        }
    }

    // ===============================================================
    // 8. User isolation
    // ===============================================================
    Given("two users with separate notifications") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userA = ObjectId.get()
        val userB = ObjectId.get()
        val headersA = userHeaders(userA)
        val headersB = userHeaders(userB)

        seedNotification(userA, NotificationType.ORDER_NEW)
        seedNotification(userA, NotificationType.ORDER_COMPLETE)
        seedNotification(userB, NotificationType.AD_STOCK_DEPLETED)

        When("user A lists notifications") {
            val response = restClient.get("/notifications", headersA)
            Then("should only see their own notifications") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 2
            }
        }

        When("user B lists notifications") {
            val response = restClient.get("/notifications", headersB)
            Then("should only see their own notifications") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 1
            }
        }
    }

    // ===============================================================
    // 9. Notification response structure
    // ===============================================================
    Given("a notification with all fields populated") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)

        val now = Instant.now().toMicros()
        val notification = seedNotification(
            userId = userId,
            type = NotificationType.ORDER_NEW,
            read = false,
            createdAt = now,
            actionUrl = "/sales/orders/abc123",
            metadata = mapOf("purchaseId" to "abc123")
        )

        When("listing notifications") {
            val response = restClient.get("/notifications", headers)
            Then("response should contain all expected fields") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                val notif = notifications[0] as Map<*, *>

                notif["id"] shouldBe notification.id
                notif["type"] shouldBe "order.new"
                notif["category"] shouldBe "orders"
                notif["severity"] shouldBe "info"
                notif["title"] shouldBe "Test Title"
                notif["message"] shouldBe "Test Message"
                notif["read"] shouldBe false
                notif["actionUrl"] shouldBe "/sales/orders/abc123"
                (notif["metadata"] as Map<*, *>)["purchaseId"] shouldBe "abc123"
                (notif["createdAt"] as Number).toLong() shouldBe now
            }
        }
    }

    // ===============================================================
    // 10. Limit clamping
    // ===============================================================
    Given("a user requesting notifications with extreme limit values") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val userId = ObjectId.get()
        val headers = userHeaders(userId)
        seedNotification(userId, NotificationType.ORDER_NEW)

        When("requesting with limit=0") {
            val response = restClient.get("/notifications?limit=0", headers)
            Then("should clamp to minimum 1 and return successfully") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val data = body["data"] as Map<*, *>
                val notifications = data["notifications"] as List<*>
                notifications shouldHaveSize 1
            }
        }

        When("requesting with limit=100") {
            val response = restClient.get("/notifications?limit=100", headers)
            Then("should clamp to max 50 and return successfully") {
                response.statusCode shouldBe 200
            }
        }
    }
})
