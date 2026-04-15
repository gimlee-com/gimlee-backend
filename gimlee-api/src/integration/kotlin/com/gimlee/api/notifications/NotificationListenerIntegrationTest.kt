package com.gimlee.api.notifications

import com.gimlee.ads.domain.WatchlistService
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.events.*
import com.gimlee.notifications.domain.model.*
import com.gimlee.notifications.persistence.NotificationRepository
import com.gimlee.notifications.persistence.model.NotificationDocument
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.math.BigDecimal
import java.time.Instant

@Import(NotificationTestConfig::class)
class NotificationListenerIntegrationTest(
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationRepository: NotificationRepository,
    private val userRoleRepository: UserRoleRepository,
    private val watchlistService: WatchlistService
) : BaseIntegrationTest({

    fun findNotificationsForUser(userId: ObjectId): List<NotificationDocument> =
        notificationRepository.findByUserId(userId, null, 100, null)

    fun findNotificationsForUser(userId: String): List<NotificationDocument> =
        findNotificationsForUser(ObjectId(userId))

    fun findNotificationsByType(userId: ObjectId, type: NotificationType): List<NotificationDocument> =
        findNotificationsForUser(userId).filter { it.type == type.slug }

    fun findNotificationsByType(userId: String, type: NotificationType): List<NotificationDocument> =
        findNotificationsByType(ObjectId(userId), type)

    beforeSpec {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
    }

    // ===============================================================
    // PURCHASE NOTIFICATIONS
    // ===============================================================
    Given("purchase event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val purchaseId = ObjectId.get()

        When("a purchase is created (status=0)") {
            eventPublisher.publishEvent(
                PurchaseEvent(
                    purchaseId = purchaseId,
                    items = listOf(PurchaseEventItem(ObjectId.get(), 2)),
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 0,
                    totalAmount = BigDecimal("25.00"),
                    timestamp = Instant.now()
                )
            )

            Then("seller should receive ORDER_NEW notification") {
                val notifications = findNotificationsByType(sellerId, NotificationType.ORDER_NEW)
                notifications shouldHaveSize 1
                val n = notifications[0]
                n.category shouldBe NotificationCategory.ORDERS.shortName
                n.metadata?.get("purchaseId") shouldBe purchaseId.toHexString()
                n.title.shouldNotBeBlank()
                n.message.shouldNotBeBlank()
                n.message shouldNotContain "gimlee.notifications"
            }
        }

        When("a purchase is completed (status=2)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PurchaseEvent(
                    purchaseId = purchaseId,
                    items = listOf(PurchaseEventItem(ObjectId.get(), 1)),
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 2,
                    totalAmount = BigDecimal("25.00"),
                    timestamp = Instant.now()
                )
            )

            Then("both buyer and seller should receive ORDER_COMPLETE notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_COMPLETE) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.ORDER_COMPLETE) shouldHaveSize 1
            }
        }

        When("a purchase times out (status=3)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PurchaseEvent(
                    purchaseId = purchaseId,
                    items = listOf(PurchaseEventItem(ObjectId.get(), 1)),
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 3,
                    totalAmount = BigDecimal("25.00"),
                    timestamp = Instant.now()
                )
            )

            Then("buyer should receive ORDER_PAYMENT_TIMEOUT notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_PAYMENT_TIMEOUT) shouldHaveSize 1
            }
            Then("seller should NOT receive a notification") {
                findNotificationsForUser(sellerId) shouldHaveSize 0
            }
        }

        When("a purchase is underpaid (status=4)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PurchaseEvent(
                    purchaseId = purchaseId,
                    items = listOf(PurchaseEventItem(ObjectId.get(), 1)),
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 4,
                    totalAmount = BigDecimal("25.00"),
                    timestamp = Instant.now()
                )
            )

            Then("buyer should receive ORDER_UNDERPAID notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_UNDERPAID) shouldHaveSize 1
            }
        }

        When("a purchase is cancelled (status=5)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PurchaseEvent(
                    purchaseId = purchaseId,
                    items = listOf(PurchaseEventItem(ObjectId.get(), 1)),
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 5,
                    totalAmount = BigDecimal("25.00"),
                    timestamp = Instant.now()
                )
            )

            Then("both buyer and seller should receive ORDER_CANCELLED notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_CANCELLED) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.ORDER_CANCELLED) shouldHaveSize 1
            }
        }
    }

    // ===============================================================
    // PAYMENT NOTIFICATIONS
    // ===============================================================
    Given("payment event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val buyerId = ObjectId.get()
        val sellerId = ObjectId.get()
        val purchaseId = ObjectId.get()

        When("payment is awaiting confirmation (status=1)") {
            eventPublisher.publishEvent(
                PaymentEvent(
                    purchaseId = purchaseId,
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 1,
                    paymentMethod = 1,
                    amount = BigDecimal("10.00"),
                    timestamp = Instant.now()
                )
            )

            Then("buyer should receive ORDER_AWAITING_PAYMENT notification") {
                val notifications = findNotificationsByType(buyerId, NotificationType.ORDER_AWAITING_PAYMENT)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("purchaseId") shouldBe purchaseId.toHexString()
            }
        }

        When("payment is overpaid (status=3)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PaymentEvent(
                    purchaseId = purchaseId,
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 3,
                    paymentMethod = 1,
                    amount = BigDecimal("15.00"),
                    timestamp = Instant.now()
                )
            )

            Then("both buyer and seller should receive ORDER_OVERPAID notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_OVERPAID) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.ORDER_OVERPAID) shouldHaveSize 1
            }
        }

        When("payment soft timeout (status=5)") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PaymentEvent(
                    purchaseId = purchaseId,
                    buyerId = buyerId,
                    sellerId = sellerId,
                    status = 5,
                    paymentMethod = 1,
                    amount = BigDecimal("10.00"),
                    timestamp = Instant.now()
                )
            )

            Then("buyer should receive ORDER_PAYMENT_TIMEOUT notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_PAYMENT_TIMEOUT) shouldHaveSize 1
            }
        }

        When("payment deadline is approaching") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                PaymentDeadlineApproachingEvent(
                    purchaseId = purchaseId,
                    buyerId = buyerId,
                    sellerId = sellerId,
                    amount = BigDecimal("10.00"),
                    deadline = Instant.now().plusSeconds(300)
                )
            )

            Then("buyer should receive ORDER_PAYMENT_DEADLINE notification") {
                findNotificationsByType(buyerId, NotificationType.ORDER_PAYMENT_DEADLINE) shouldHaveSize 1
            }
        }
    }

    // ===============================================================
    // ACCOUNT NOTIFICATIONS
    // ===============================================================
    Given("account event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("a new user registers") {
            val userId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                UserRegisteredEvent(userId = userId, countryOfResidence = "US")
            )

            Then("user should receive ACCOUNT_WELCOME notification") {
                val notifications = findNotificationsByType(userId, NotificationType.ACCOUNT_WELCOME)
                notifications shouldHaveSize 1
                notifications[0].suggestedAction?.type shouldBe SuggestedActionType.GETTING_STARTED
            }
        }

        When("a user is banned") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val userId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                UserBannedEvent(
                    userId = userId,
                    username = "testuser",
                    email = "test@test.com",
                    reason = "Spam",
                    bannedBy = "admin1",
                    bannedUntil = null
                )
            )

            Then("user should receive ACCOUNT_BAN notification with reason") {
                val notifications = findNotificationsByType(userId, NotificationType.ACCOUNT_BAN)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("reason") shouldBe "Spam"
            }
        }

        When("a user is unbanned") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val userId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                UserUnbannedEvent(userId = userId, unbannedBy = "admin1")
            )

            Then("user should receive ACCOUNT_UNBAN notification") {
                findNotificationsByType(userId, NotificationType.ACCOUNT_UNBAN) shouldHaveSize 1
            }
        }

        When("a ban is expiring soon") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val userId = ObjectId.get().toHexString()
            val bannedUntil = Instant.now().plusSeconds(86400).toEpochMilli() * 1000
            eventPublisher.publishEvent(
                BanExpiryApproachingEvent(userId = userId, bannedUntil = bannedUntil)
            )

            Then("user should receive ACCOUNT_BAN_EXPIRING notification") {
                val notifications = findNotificationsByType(userId, NotificationType.ACCOUNT_BAN_EXPIRING)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("bannedUntil") shouldNotBe null
            }
        }
    }

    // ===============================================================
    // AD NOTIFICATIONS
    // ===============================================================
    Given("ad event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("an ad's stock is depleted") {
            val sellerId = ObjectId.get().toHexString()
            val adId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                AdStatusChangedEvent(
                    adId = adId,
                    sellerId = sellerId,
                    oldStatus = "ACTIVE",
                    newStatus = "INACTIVE",
                    categoryIds = listOf(1),
                    reason = AdStatusChangedEvent.Reason.STOCK_DEPLETED
                )
            )

            Then("seller should receive AD_STOCK_DEPLETED notification") {
                val notifications = findNotificationsByType(sellerId, NotificationType.AD_STOCK_DEPLETED)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("adId") shouldBe adId
            }
        }

        When("an ad's category is hidden") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val sellerId = ObjectId.get().toHexString()
            val adId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                AdStatusChangedEvent(
                    adId = adId,
                    sellerId = sellerId,
                    oldStatus = "ACTIVE",
                    newStatus = "INACTIVE",
                    categoryIds = listOf(1),
                    reason = AdStatusChangedEvent.Reason.CATEGORY_HIDDEN
                )
            )

            Then("seller should receive AD_CATEGORY_HIDDEN notification") {
                findNotificationsByType(sellerId, NotificationType.AD_CATEGORY_HIDDEN) shouldHaveSize 1
            }
        }
    }

    // ===============================================================
    // AD WATCHLIST NOTIFICATIONS (fan-out)
    // ===============================================================
    Given("ad watchlist event listeners with watchers") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val sellerId = ObjectId.get()
        val watcher1 = ObjectId.get()
        val watcher2 = ObjectId.get()
        val adId = ObjectId.get().toHexString()

        // Set up watchlist entries
        watchlistService.addToWatchlist(watcher1.toHexString(), adId)
        watchlistService.addToWatchlist(watcher2.toHexString(), adId)
        watchlistService.addToWatchlist(sellerId.toHexString(), adId)

        When("ad is deactivated by user action") {
            eventPublisher.publishEvent(
                AdStatusChangedEvent(
                    adId = adId,
                    sellerId = sellerId.toHexString(),
                    oldStatus = "ACTIVE",
                    newStatus = "INACTIVE",
                    categoryIds = listOf(1),
                    reason = AdStatusChangedEvent.Reason.USER_ACTION
                )
            )

            Then("watchers should receive AD_WATCHLIST_DEACTIVATED (excluding seller)") {
                findNotificationsByType(watcher1, NotificationType.AD_WATCHLIST_DEACTIVATED) shouldHaveSize 1
                findNotificationsByType(watcher2, NotificationType.AD_WATCHLIST_DEACTIVATED) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.AD_WATCHLIST_DEACTIVATED) shouldHaveSize 0
            }
        }

        When("ad price changes") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                AdPriceChangedEvent(
                    adId = adId,
                    sellerId = sellerId.toHexString(),
                    adTitle = "Test Ad",
                    oldPrice = "10.00",
                    newPrice = "8.50",
                    currency = "ARRR"
                )
            )

            Then("watchers should receive AD_WATCHLIST_PRICE_CHANGE (excluding seller)") {
                findNotificationsByType(watcher1, NotificationType.AD_WATCHLIST_PRICE_CHANGE) shouldHaveSize 1
                findNotificationsByType(watcher2, NotificationType.AD_WATCHLIST_PRICE_CHANGE) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.AD_WATCHLIST_PRICE_CHANGE) shouldHaveSize 0
            }
        }

        When("ad is restocked") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            eventPublisher.publishEvent(
                AdRestockedEvent(
                    adId = adId,
                    sellerId = sellerId.toHexString(),
                    adTitle = "Test Ad",
                    newStock = 5
                )
            )

            Then("watchers should receive AD_WATCHLIST_BACK_IN_STOCK (excluding seller)") {
                findNotificationsByType(watcher1, NotificationType.AD_WATCHLIST_BACK_IN_STOCK) shouldHaveSize 1
                findNotificationsByType(watcher2, NotificationType.AD_WATCHLIST_BACK_IN_STOCK) shouldHaveSize 1
                findNotificationsByType(sellerId, NotificationType.AD_WATCHLIST_BACK_IN_STOCK) shouldHaveSize 0
            }
        }
    }

    // ===============================================================
    // Q&A NOTIFICATIONS
    // ===============================================================
    Given("Q&A event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("a question is asked") {
            val sellerId = ObjectId.get().toHexString()
            val authorId = ObjectId.get().toHexString()
            val adId = ObjectId.get().toHexString()
            val questionId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                QuestionAskedEvent(
                    questionId = questionId,
                    adId = adId,
                    adTitle = "Test Ad",
                    authorId = authorId,
                    sellerId = sellerId
                )
            )

            Then("seller should receive QA_NEW_QUESTION notification") {
                val notifications = findNotificationsByType(sellerId, NotificationType.QA_NEW_QUESTION)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("adId") shouldBe adId
                notifications[0].metadata?.get("questionId") shouldBe questionId
                notifications[0].message shouldContain "Test Ad"
            }
        }

        When("a question is answered") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val questionAuthorId = ObjectId.get().toHexString()
            val answerAuthorId = ObjectId.get().toHexString()
            val adId = ObjectId.get().toHexString()
            val questionId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                QuestionAnsweredEvent(
                    questionId = questionId,
                    answerId = ObjectId.get().toHexString(),
                    adId = adId,
                    adTitle = "Answered Ad",
                    questionAuthorId = questionAuthorId,
                    answerAuthorId = answerAuthorId,
                    answerType = "SELLER"
                )
            )

            Then("question author should receive QA_NEW_ANSWER notification") {
                val notifications = findNotificationsByType(questionAuthorId, NotificationType.QA_NEW_ANSWER)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("adId") shouldBe adId
                notifications[0].metadata?.get("questionId") shouldBe questionId
                notifications[0].message shouldContain "Answered Ad"
            }
        }

        When("upvote milestone is reached") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val sellerId = ObjectId.get().toHexString()
            val adId = ObjectId.get().toHexString()
            val questionId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                QuestionUpvoteMilestoneEvent(
                    questionId = questionId,
                    adId = adId,
                    adTitle = "Popular Ad",
                    sellerId = sellerId,
                    upvoteCount = 10
                )
            )

            Then("seller should receive QA_UPVOTE_MILESTONE notification") {
                val notifications = findNotificationsByType(sellerId, NotificationType.QA_UPVOTE_MILESTONE)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("upvoteCount") shouldBe "10"
                notifications[0].message shouldContain "Popular Ad"
                notifications[0].message shouldContain "10"
            }
        }
    }

    // ===============================================================
    // TICKET NOTIFICATIONS
    // ===============================================================
    Given("ticket event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("a support agent replies to a ticket") {
            val creatorId = ObjectId.get().toHexString()
            val ticketId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                TicketReplyEvent(
                    ticketId = ticketId,
                    ticketCreatorId = creatorId,
                    messageId = ObjectId.get().toHexString(),
                    authorId = ObjectId.get().toHexString(),
                    authorRole = "SUPPORT"
                )
            )

            Then("ticket creator should receive TICKET_REPLY notification") {
                val notifications = findNotificationsByType(creatorId, NotificationType.TICKET_REPLY)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("ticketId") shouldBe ticketId
            }
        }

        When("a non-support user replies to a ticket") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val creatorId = ObjectId.get().toHexString()
            val ticketId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                TicketReplyEvent(
                    ticketId = ticketId,
                    ticketCreatorId = creatorId,
                    messageId = ObjectId.get().toHexString(),
                    authorId = creatorId,
                    authorRole = "USER"
                )
            )

            Then("no notification should be created") {
                findNotificationsForUser(creatorId) shouldHaveSize 0
            }
        }

        When("a ticket status changes to AWAITING_USER") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val creatorId = ObjectId.get().toHexString()
            val ticketId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                TicketUpdatedEvent(
                    ticketId = ticketId,
                    ticketCreatorId = creatorId,
                    updatedBy = ObjectId.get().toHexString(),
                    changes = mapOf("status" to "AWAITING_USER")
                )
            )

            Then("creator should receive TICKET_AWAITING_USER notification") {
                findNotificationsByType(creatorId, NotificationType.TICKET_AWAITING_USER) shouldHaveSize 1
            }
        }

        When("a ticket status changes to RESOLVED") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val creatorId = ObjectId.get().toHexString()
            val ticketId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                TicketUpdatedEvent(
                    ticketId = ticketId,
                    ticketCreatorId = creatorId,
                    updatedBy = ObjectId.get().toHexString(),
                    changes = mapOf("status" to "RESOLVED")
                )
            )

            Then("creator should receive TICKET_STATUS_CHANGE notification") {
                val notifications = findNotificationsByType(creatorId, NotificationType.TICKET_STATUS_CHANGE)
                notifications shouldHaveSize 1
                notifications[0].metadata?.get("status") shouldBe "RESOLVED"
            }
        }

        When("a ticket is updated without status change") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val creatorId = ObjectId.get().toHexString()
            val ticketId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                TicketUpdatedEvent(
                    ticketId = ticketId,
                    ticketCreatorId = creatorId,
                    updatedBy = ObjectId.get().toHexString(),
                    changes = mapOf("priority" to "HIGH")
                )
            )

            Then("no notification should be created") {
                findNotificationsForUser(creatorId) shouldHaveSize 0
            }
        }
    }

    // ===============================================================
    // REPORT NOTIFICATIONS
    // ===============================================================
    Given("report event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("a report is resolved with USER_WARNED resolution") {
            val reporterId = ObjectId.get().toHexString()
            val targetId = ObjectId.get().toHexString()
            val reportId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                ReportResolvedEvent(
                    reportId = reportId,
                    reporterId = reporterId,
                    targetId = targetId,
                    targetType = "AD",
                    resolution = "USER_WARNED",
                    resolvedBy = ObjectId.get().toHexString()
                )
            )

            Then("reporter should receive REPORT_RESOLVED notification") {
                findNotificationsByType(reporterId, NotificationType.REPORT_RESOLVED) shouldHaveSize 1
            }
            Then("target should receive MODERATION_WARNING notification") {
                findNotificationsByType(targetId, NotificationType.MODERATION_WARNING) shouldHaveSize 1
            }
        }

        When("a report is resolved with CONTENT_REMOVED resolution") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val reporterId = ObjectId.get().toHexString()
            val targetId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                ReportResolvedEvent(
                    reportId = ObjectId.get().toHexString(),
                    reporterId = reporterId,
                    targetId = targetId,
                    targetType = "AD",
                    resolution = "CONTENT_REMOVED",
                    resolvedBy = ObjectId.get().toHexString()
                )
            )

            Then("reporter should receive REPORT_RESOLVED notification") {
                findNotificationsByType(reporterId, NotificationType.REPORT_RESOLVED) shouldHaveSize 1
            }
            Then("target should receive MODERATION_CONTENT_REMOVED notification") {
                findNotificationsByType(targetId, NotificationType.MODERATION_CONTENT_REMOVED) shouldHaveSize 1
            }
        }

        When("a report is resolved with a neutral resolution") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val reporterId = ObjectId.get().toHexString()
            val targetId = ObjectId.get().toHexString()

            eventPublisher.publishEvent(
                ReportResolvedEvent(
                    reportId = ObjectId.get().toHexString(),
                    reporterId = reporterId,
                    targetId = targetId,
                    targetType = "AD",
                    resolution = "NO_ACTION",
                    resolvedBy = ObjectId.get().toHexString()
                )
            )

            Then("reporter should receive REPORT_RESOLVED, target should NOT") {
                findNotificationsByType(reporterId, NotificationType.REPORT_RESOLVED) shouldHaveSize 1
                findNotificationsForUser(targetId) shouldHaveSize 0
            }
        }
    }

    // ===============================================================
    // ADMIN NOTIFICATIONS
    // ===============================================================
    Given("admin event listeners") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
        val admin1 = ObjectId.get()
        val admin2 = ObjectId.get()
        userRoleRepository.add(admin1, Role.ADMIN)
        userRoleRepository.add(admin2, Role.ADMIN)

        When("a report is submitted") {
            val reportId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                ReportSubmittedEvent(
                    reportId = reportId,
                    targetId = ObjectId.get().toHexString(),
                    targetType = "AD",
                    contextId = null,
                    reporterId = ObjectId.get().toHexString(),
                    reason = "Scam"
                )
            )

            Then("all admins should receive ADMIN_NEW_REPORT notification") {
                findNotificationsByType(admin1, NotificationType.ADMIN_NEW_REPORT) shouldHaveSize 1
                findNotificationsByType(admin2, NotificationType.ADMIN_NEW_REPORT) shouldHaveSize 1

                val n = findNotificationsByType(admin1, NotificationType.ADMIN_NEW_REPORT)[0]
                n.metadata?.get("reportId") shouldBe reportId
                n.metadata?.get("targetType") shouldBe "AD"
            }
        }

        When("a report is assigned") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val reportId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                ReportAssignedEvent(
                    reportId = reportId,
                    assigneeId = admin1.toHexString(),
                    assignedBy = admin2.toHexString()
                )
            )

            Then("only the assignee should receive ADMIN_REPORT_ASSIGNED notification") {
                findNotificationsByType(admin1, NotificationType.ADMIN_REPORT_ASSIGNED) shouldHaveSize 1
                findNotificationsByType(admin2, NotificationType.ADMIN_REPORT_ASSIGNED) shouldHaveSize 0
            }
        }

        When("a ticket is created") {
            mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)
            val ticketId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                TicketCreatedEvent(
                    ticketId = ticketId,
                    creatorId = ObjectId.get().toHexString(),
                    category = "TECHNICAL_BUG"
                )
            )

            Then("all admins should receive ADMIN_NEW_TICKET notification") {
                findNotificationsByType(admin1, NotificationType.ADMIN_NEW_TICKET) shouldHaveSize 1
                findNotificationsByType(admin2, NotificationType.ADMIN_NEW_TICKET) shouldHaveSize 1

                val n = findNotificationsByType(admin1, NotificationType.ADMIN_NEW_TICKET)[0]
                n.metadata?.get("ticketId") shouldBe ticketId
                n.metadata?.get("category") shouldBe "TECHNICAL_BUG"
            }
        }
    }

    // ===============================================================
    // i18n LOCALIZATION
    // ===============================================================
    Given("notification localization") {
        mongoTemplate.remove(Query(), NotificationRepository.COLLECTION_NAME)

        When("a notification is created for a user with default language") {
            val userId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(
                UserRegisteredEvent(userId = userId, countryOfResidence = "US")
            )

            Then("notification should have localized title and message (not raw keys)") {
                val notifications = findNotificationsByType(userId, NotificationType.ACCOUNT_WELCOME)
                notifications shouldHaveSize 1
                notifications[0].title shouldNotContain "gimlee.notifications"
                notifications[0].message shouldNotContain "gimlee.notifications"
                notifications[0].title.shouldNotBeBlank()
                notifications[0].message.shouldNotBeBlank()
            }
        }
    }
})
