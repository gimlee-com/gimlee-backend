package com.gimlee.api.playground.support.data

import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import com.gimlee.common.toMicros
import com.gimlee.common.UUIDv7
import com.gimlee.support.report.domain.model.*
import com.gimlee.support.report.persistence.ReportRepository
import com.gimlee.support.report.persistence.model.ReportDocument
import com.gimlee.support.ticket.domain.model.*
import com.gimlee.support.ticket.persistence.TicketMessageRepository
import com.gimlee.support.ticket.persistence.TicketRepository
import com.gimlee.support.ticket.persistence.model.TicketDocument
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument
import org.apache.logging.log4j.LogManager
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant

@Profile("local", "dev", "test")
@Lazy(true)
@Component
class SupportPopulator(
    private val reportRepository: ReportRepository,
    private val ticketRepository: TicketRepository,
    private val ticketMessageRepository: TicketMessageRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private val log = LogManager.getLogger()

        private const val SUPPORT_USERNAME = "playground_support"
        private const val REPORTER_USERNAME_PREFIX = "playground_reporter_"
        private const val MIN_REPORTERS = 5
        private const val PASSWORD = "Password1"

        private val HOUR_MICROS = 3_600_000_000L
        private val DAY_MICROS = 24 * HOUR_MICROS
    }

    fun populateSupport() {
        if (hasExistingData()) {
            log.warn("Support data already exists. Skipping population.")
            return
        }

        val users = ensureUsers()
        val supportUser = users.supportUser
        val reporters = users.reporters

        log.info("Populating support data with {} reporters and 1 support user.", reporters.size)

        val reportIds = populateReports(reporters, supportUser)
        populateTickets(reporters, supportUser, reportIds)

        log.info("Support data population complete: {} reports and tickets created.", reportIds.size)
    }

    private fun hasExistingData(): Boolean {
        val reportCount = reportRepository.countByStatusIn(ReportStatus.entries)
        return reportCount > 0
    }

    private fun populateReports(reporters: List<ObjectId>, supportUser: ObjectId): List<ObjectId> {
        val now = Instant.now().toMicros()
        val reportIds = mutableListOf<ObjectId>()

        val sharedAdTarget = ObjectId()
        val sharedUserTarget = ObjectId()

        reportIds += createOpenReports(reporters, now)
        reportIds += createInReviewReports(reporters, supportUser, now)
        reportIds += createResolvedReports(reporters, supportUser, now)
        reportIds += createDismissedReports(reporters, supportUser, now)
        reportIds += createSiblingReports(reporters, supportUser, sharedAdTarget, sharedUserTarget, now)

        log.info("Created {} reports.", reportIds.size)
        return reportIds
    }

    private fun createOpenReports(reporters: List<ObjectId>, now: Long): List<ObjectId> {
        val reports = listOf(
            ReportTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.SPAM,
                targetTitle = "Suspicious ad: Free crypto giveaway",
                description = "This ad looks like a scam. It promises free cryptocurrency for signing up.",
                timeOffset = -2 * HOUR_MICROS
            ),
            ReportTemplate(
                targetType = ReportTargetType.MESSAGE,
                reason = ReportReason.HARASSMENT,
                targetTitle = "Chat message in trade #4821",
                description = "The buyer sent threatening messages after I declined the offer.",
                timeOffset = -45 * 60_000_000L
            ),
            ReportTemplate(
                targetType = ReportTargetType.QUESTION,
                reason = ReportReason.INAPPROPRIATE_CONTENT,
                targetTitle = "Question on: Vintage collectible coins",
                description = null,
                timeOffset = -15 * 60_000_000L
            ),
            ReportTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.COUNTERFEIT,
                targetTitle = "Designer watch - brand new sealed",
                description = "The photos appear to show a counterfeit product. The logo placement is wrong.",
                timeOffset = -5 * 60_000_000L
            )
        )

        return reports.mapIndexed { index, template ->
            val reporterIdx = index % reporters.size
            val createdAt = now + template.timeOffset
            saveReport(
                targetType = template.targetType,
                reason = template.reason,
                targetTitle = template.targetTitle,
                description = template.description,
                status = ReportStatus.OPEN,
                reporterId = reporters[reporterIdx],
                createdAt = createdAt,
                timeline = listOf(createdTimeline(reporters[reporterIdx], createdAt))
            )
        }
    }

    private fun createInReviewReports(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        now: Long
    ): List<ObjectId> {
        val reports = listOf(
            ReportTemplate(
                targetType = ReportTargetType.USER,
                reason = ReportReason.FRAUD,
                targetTitle = "User: shadyTrader99",
                description = "This user has been conducting bait-and-switch trades. Listed one item but shipped a different, cheaper version.",
                timeOffset = -6 * HOUR_MICROS,
                note = "Checking transaction history for pattern of behavior."
            ),
            ReportTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.INAPPROPRIATE_CONTENT,
                targetTitle = "Electronics bundle - various items",
                description = "The listing contains images that violate community guidelines.",
                timeOffset = -12 * HOUR_MICROS,
                note = null
            ),
            ReportTemplate(
                targetType = ReportTargetType.ANSWER,
                reason = ReportReason.SPAM,
                targetTitle = "Answer on: How to verify PirateChain payments?",
                description = "This answer is a thinly disguised advertisement for an external service.",
                timeOffset = -3 * HOUR_MICROS,
                note = "User has posted similar promotional content across multiple Q&A threads."
            )
        )

        return reports.mapIndexed { index, template ->
            val reporterIdx = (index + 1) % reporters.size
            val createdAt = now + template.timeOffset
            val assignedAt = createdAt + 30 * 60_000_000L

            val timeline = mutableListOf(
                createdTimeline(reporters[reporterIdx], createdAt),
                assignedTimeline(supportUser, assignedAt),
                statusChangedTimeline(supportUser, assignedAt + 1_000_000L, "OPEN → IN_REVIEW")
            )
            if (template.note != null) {
                timeline += noteTimeline(supportUser, assignedAt + HOUR_MICROS, template.note)
            }

            saveReport(
                targetType = template.targetType,
                reason = template.reason,
                targetTitle = template.targetTitle,
                description = template.description,
                status = ReportStatus.IN_REVIEW,
                reporterId = reporters[reporterIdx],
                assigneeId = supportUser,
                internalNotes = template.note,
                createdAt = createdAt,
                updatedAt = timeline.last().getLong(ReportDocument.TL_FIELD_CREATED_AT),
                timeline = timeline
            )
        }
    }

    private fun createResolvedReports(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        now: Long
    ): List<ObjectId> {
        data class ResolvedTemplate(
            val targetType: ReportTargetType,
            val reason: ReportReason,
            val targetTitle: String,
            val description: String?,
            val resolution: ReportResolution,
            val internalNotes: String,
            val timeOffset: Long
        )

        val templates = listOf(
            ResolvedTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.COUNTERFEIT,
                targetTitle = "Luxury handbag - limited edition",
                description = "Seller is using stolen product photos from a legitimate retailer.",
                resolution = ReportResolution.CONTENT_REMOVED,
                internalNotes = "Confirmed via reverse image search. Ad removed and seller notified.",
                timeOffset = -3 * DAY_MICROS
            ),
            ResolvedTemplate(
                targetType = ReportTargetType.USER,
                reason = ReportReason.HARASSMENT,
                targetTitle = "User: aggressiveBuyer42",
                description = "This user sent abusive messages to multiple sellers after failed negotiations.",
                resolution = ReportResolution.USER_WARNED,
                internalNotes = "First offense. Warning issued. Will escalate to ban on repeat.",
                timeOffset = -2 * DAY_MICROS
            ),
            ResolvedTemplate(
                targetType = ReportTargetType.MESSAGE,
                reason = ReportReason.FRAUD,
                targetTitle = "Chat message in trade #7293",
                description = "Seller sent a fake payment confirmation screenshot.",
                resolution = ReportResolution.USER_BANNED,
                internalNotes = "Clear evidence of fabricated payment proof. Account permanently banned.",
                timeOffset = -5 * DAY_MICROS
            ),
            ResolvedTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.COPYRIGHT,
                targetTitle = "Digital art prints - set of 10",
                description = "These are copies of a well-known artist's work being sold without permission.",
                resolution = ReportResolution.CONTENT_REMOVED,
                internalNotes = "DMCA request verified. Content removed per copyright policy.",
                timeOffset = -7 * DAY_MICROS
            )
        )

        return templates.mapIndexed { index, template ->
            val reporterIdx = (index + 2) % reporters.size
            val createdAt = now + template.timeOffset
            val assignedAt = createdAt + HOUR_MICROS
            val reviewedAt = assignedAt + 2 * HOUR_MICROS
            val resolvedAt = reviewedAt + HOUR_MICROS

            val timeline = listOf(
                createdTimeline(reporters[reporterIdx], createdAt),
                assignedTimeline(supportUser, assignedAt),
                statusChangedTimeline(supportUser, reviewedAt, "OPEN → IN_REVIEW"),
                resolvedTimeline(supportUser, resolvedAt, template.resolution.name)
            )

            saveReport(
                targetType = template.targetType,
                reason = template.reason,
                targetTitle = template.targetTitle,
                description = template.description,
                status = ReportStatus.RESOLVED,
                reporterId = reporters[reporterIdx],
                assigneeId = supportUser,
                resolution = template.resolution,
                resolvedBy = supportUser,
                resolvedAt = resolvedAt,
                internalNotes = template.internalNotes,
                createdAt = createdAt,
                updatedAt = resolvedAt,
                timeline = timeline
            )
        }
    }

    private fun createDismissedReports(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        now: Long
    ): List<ObjectId> {
        data class DismissedTemplate(
            val targetType: ReportTargetType,
            val reason: ReportReason,
            val targetTitle: String,
            val description: String?,
            val resolution: ReportResolution,
            val internalNotes: String,
            val timeOffset: Long
        )

        val templates = listOf(
            DismissedTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.WRONG_CATEGORY,
                targetTitle = "Handmade jewelry set",
                description = "This should be in the Accessories category, not Electronics.",
                resolution = ReportResolution.NO_VIOLATION,
                internalNotes = "Reviewed the listing. Category is actually correct — it's electronic jewelry (LED necklace).",
                timeOffset = -4 * DAY_MICROS
            ),
            DismissedTemplate(
                targetType = ReportTargetType.QUESTION,
                reason = ReportReason.SPAM,
                targetTitle = "Question on: Crypto mining rig setup guide",
                description = "This question seems like self-promotion.",
                resolution = ReportResolution.NO_VIOLATION,
                internalNotes = "Legitimate question. Reporter may have misunderstood the context.",
                timeOffset = -1 * DAY_MICROS
            ),
            DismissedTemplate(
                targetType = ReportTargetType.USER,
                reason = ReportReason.FRAUD,
                targetTitle = "User: newTrader2024",
                description = "Suspicious account activity detected.",
                resolution = ReportResolution.DUPLICATE,
                internalNotes = "Duplicate of report handled earlier. Same target, same issue.",
                timeOffset = -8 * DAY_MICROS
            ),
            DismissedTemplate(
                targetType = ReportTargetType.AD,
                reason = ReportReason.OTHER,
                targetTitle = "Vintage vinyl records collection",
                description = "Price seems unreasonably high for these items.",
                resolution = ReportResolution.NO_VIOLATION,
                internalNotes = "Pricing is at the seller's discretion. No policy violation found.",
                timeOffset = -10 * DAY_MICROS
            )
        )

        return templates.mapIndexed { index, template ->
            val reporterIdx = (index + 3) % reporters.size
            val createdAt = now + template.timeOffset
            val assignedAt = createdAt + 2 * HOUR_MICROS
            val resolvedAt = assignedAt + HOUR_MICROS

            val timeline = listOf(
                createdTimeline(reporters[reporterIdx], createdAt),
                assignedTimeline(supportUser, assignedAt),
                resolvedTimeline(supportUser, resolvedAt, template.resolution.name)
            )

            saveReport(
                targetType = template.targetType,
                reason = template.reason,
                targetTitle = template.targetTitle,
                description = template.description,
                status = ReportStatus.DISMISSED,
                reporterId = reporters[reporterIdx],
                assigneeId = supportUser,
                resolution = template.resolution,
                resolvedBy = supportUser,
                resolvedAt = resolvedAt,
                internalNotes = template.internalNotes,
                createdAt = createdAt,
                updatedAt = resolvedAt,
                timeline = timeline
            )
        }
    }

    private fun createSiblingReports(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        sharedAdTarget: ObjectId,
        sharedUserTarget: ObjectId,
        now: Long
    ): List<ObjectId> {
        val ids = mutableListOf<ObjectId>()
        val adCreatedAt = now - 18 * HOUR_MICROS

        for (i in 0 until 3) {
            val reporterIdx = i % reporters.size
            val offset = i * 20 * 60_000_000L
            val createdAt = adCreatedAt + offset

            ids += saveReport(
                targetId = sharedAdTarget,
                targetType = ReportTargetType.AD,
                reason = if (i == 0) ReportReason.SPAM else ReportReason.FRAUD,
                targetTitle = "Too-good-to-be-true electronics deal",
                description = when (i) {
                    0 -> "Obvious spam listing with unrealistic pricing."
                    1 -> "This listing is a known scam pattern."
                    else -> "Multiple red flags in this ad."
                },
                status = ReportStatus.IN_REVIEW,
                reporterId = reporters[reporterIdx],
                assigneeId = supportUser,
                siblingCount = 3,
                createdAt = createdAt,
                timeline = listOf(
                    createdTimeline(reporters[reporterIdx], createdAt),
                    assignedTimeline(supportUser, createdAt + 10 * 60_000_000L),
                    statusChangedTimeline(supportUser, createdAt + 11 * 60_000_000L, "OPEN → IN_REVIEW")
                )
            )
        }

        val userCreatedAt = now - 4 * DAY_MICROS
        for (i in 0 until 2) {
            val reporterIdx = (i + 3) % reporters.size
            val offset = i * HOUR_MICROS
            val createdAt = userCreatedAt + offset

            ids += saveReport(
                targetId = sharedUserTarget,
                targetType = ReportTargetType.USER,
                reason = ReportReason.HARASSMENT,
                targetTitle = "User: toxicCommunityMember",
                description = if (i == 0) "Repeated hostile behavior in chat." else "Harassing multiple users in marketplace.",
                status = ReportStatus.OPEN,
                reporterId = reporters[reporterIdx],
                siblingCount = 2,
                createdAt = createdAt,
                timeline = listOf(createdTimeline(reporters[reporterIdx], createdAt))
            )
        }

        return ids
    }

    private fun populateTickets(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        reportIds: List<ObjectId>
    ) {
        val now = Instant.now().toMicros()
        var ticketCount = 0

        ticketCount += createOpenTickets(reporters, now)
        ticketCount += createInProgressTickets(reporters, supportUser, now)
        ticketCount += createAwaitingUserTicket(reporters, supportUser, now)
        ticketCount += createResolvedTicket(reporters, supportUser, reportIds, now)
        ticketCount += createClosedTickets(reporters, supportUser, reportIds, now)

        log.info("Created {} tickets with messages.", ticketCount)
    }

    private fun createOpenTickets(reporters: List<ObjectId>, now: Long): Int {
        saveTicketWithMessages(
            subject = "Cannot verify my PirateChain payment",
            category = TicketCategory.PAYMENT_PROBLEM,
            priority = TicketPriority.HIGH,
            status = TicketStatus.OPEN,
            creatorId = reporters[0],
            createdAt = now - 30 * 60_000_000L,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[0],
                    "I sent ARRR to the seller's address 2 hours ago but the payment status still shows as pending. " +
                        "The transaction is confirmed on the blockchain explorer. Transaction ID: abc123def456. " +
                        "Can someone help me verify this?"
                )
            )
        )

        saveTicketWithMessages(
            subject = "How do I change my display name?",
            category = TicketCategory.ACCOUNT_ISSUE,
            priority = TicketPriority.LOW,
            status = TicketStatus.OPEN,
            creatorId = reporters[1],
            createdAt = now - 10 * 60_000_000L,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[1],
                    "I want to update my display name but I can't find the option in settings. Where is this feature?"
                )
            )
        )

        return 2
    }

    private fun createInProgressTickets(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        now: Long
    ): Int {
        saveTicketWithMessages(
            subject = "Seller shipped wrong item - need refund",
            category = TicketCategory.ORDER_DISPUTE,
            priority = TicketPriority.HIGH,
            status = TicketStatus.IN_PROGRESS,
            creatorId = reporters[2],
            assigneeId = supportUser,
            createdAt = now - 2 * DAY_MICROS,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[2],
                    "I ordered a GPU (RTX 4080) but received an RTX 3060 instead. " +
                        "The seller is not responding to my chat messages. I have photos of what I received."
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "Thank you for reporting this. I've reviewed your order and can see the discrepancy. " +
                        "I'm reaching out to the seller now. Could you upload the photos of the received item to this ticket?"
                ),
                MessageTemplate(
                    TicketMessageRole.USER, reporters[2],
                    "I've attached the photos. You can clearly see it's a different model than what was listed. " +
                        "The seller's ad specifically showed the RTX 4080 box."
                )
            )
        )

        saveTicketWithMessages(
            subject = "App crashes when opening chat on mobile",
            category = TicketCategory.TECHNICAL_BUG,
            priority = TicketPriority.MEDIUM,
            status = TicketStatus.IN_PROGRESS,
            creatorId = reporters[3],
            assigneeId = supportUser,
            createdAt = now - DAY_MICROS,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[3],
                    "Every time I try to open a chat conversation on my Android phone, the app freezes and then crashes. " +
                        "This has been happening since the last update. Android 14, Samsung Galaxy S24."
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "I'm sorry for the inconvenience. We've identified a known issue with the chat module " +
                        "on certain Android 14 devices. Our development team is working on a fix. " +
                        "In the meantime, could you try clearing the app cache and restarting?"
                )
            )
        )

        return 2
    }

    private fun createAwaitingUserTicket(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        now: Long
    ): Int {
        saveTicketWithMessages(
            subject = "Suggestion: Add Monero (XMR) support",
            category = TicketCategory.FEATURE_REQUEST,
            priority = TicketPriority.LOW,
            status = TicketStatus.AWAITING_USER,
            creatorId = reporters[4 % reporters.size],
            assigneeId = supportUser,
            createdAt = now - 3 * DAY_MICROS,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[4 % reporters.size],
                    "I'd love to see Monero support added to the platform. Many privacy-focused traders " +
                        "prefer XMR. Are there any plans for this?"
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "Thank you for the suggestion! Monero integration is actually on our roadmap. " +
                        "Could you elaborate on which specific features would be most important to you? " +
                        "For example, would you primarily use it for buying, selling, or both?"
                )
            )
        )

        return 1
    }

    private fun createResolvedTicket(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        reportIds: List<ObjectId>,
        now: Long
    ): Int {
        val relatedReports = reportIds.take(1)
        val createdAt = now - 5 * DAY_MICROS
        val resolvedAt = createdAt + 4 * HOUR_MICROS

        saveTicketWithMessages(
            subject = "Account locked after too many login attempts",
            category = TicketCategory.ACCOUNT_ISSUE,
            priority = TicketPriority.URGENT,
            status = TicketStatus.RESOLVED,
            creatorId = reporters[0],
            assigneeId = supportUser,
            relatedReportIds = relatedReports,
            createdAt = createdAt,
            resolvedAt = resolvedAt,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[0],
                    "My account got locked and I can't log in. I tried my password multiple times " +
                        "because I thought I was entering it wrong. I have an active trade in progress!"
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "I've verified your identity and unlocked your account. The lockout was triggered by " +
                        "our brute-force protection after 5 failed attempts. You should be able to log in now. " +
                        "I recommend enabling two-factor authentication for extra security."
                ),
                MessageTemplate(
                    TicketMessageRole.USER, reporters[0],
                    "It works now, thank you! I'll set up 2FA right away."
                )
            )
        )

        return 1
    }

    private fun createClosedTickets(
        reporters: List<ObjectId>,
        supportUser: ObjectId,
        reportIds: List<ObjectId>,
        now: Long
    ): Int {
        val relatedReports = if (reportIds.size >= 3) reportIds.subList(1, 3) else emptyList()
        val createdAt1 = now - 14 * DAY_MICROS
        val resolvedAt1 = createdAt1 + 2 * DAY_MICROS

        saveTicketWithMessages(
            subject = "Reported user is still active despite ban",
            category = TicketCategory.SAFETY_CONCERN,
            priority = TicketPriority.URGENT,
            status = TicketStatus.CLOSED,
            creatorId = reporters[1],
            assigneeId = supportUser,
            relatedReportIds = relatedReports,
            createdAt = createdAt1,
            resolvedAt = resolvedAt1,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[1],
                    "I reported a user for fraud last week and was told they'd be banned, but I can still " +
                        "see their listings active on the marketplace. Is the ban not working?"
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "Thank you for following up. I've investigated and found that the user created a new account. " +
                        "We've now banned the new account as well and implemented additional measures to prevent " +
                        "circumvention. Their listings have been removed."
                ),
                MessageTemplate(
                    TicketMessageRole.USER, reporters[1],
                    "Great, I can confirm the listings are gone now. Thanks for the quick action!"
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "Glad to hear it's resolved. We take ban evasion seriously. Closing this ticket — " +
                        "feel free to open a new one if you notice anything else."
                )
            )
        )

        val createdAt2 = now - 21 * DAY_MICROS
        val resolvedAt2 = createdAt2 + DAY_MICROS

        saveTicketWithMessages(
            subject = "Payment stuck in pending for 3 days",
            category = TicketCategory.PAYMENT_PROBLEM,
            priority = TicketPriority.HIGH,
            status = TicketStatus.CLOSED,
            creatorId = reporters[2],
            assigneeId = supportUser,
            createdAt = createdAt2,
            resolvedAt = resolvedAt2,
            messages = listOf(
                MessageTemplate(
                    TicketMessageRole.USER, reporters[2],
                    "My ARRR payment has been stuck in 'pending' status for 3 days. " +
                        "The blockchain shows it as confirmed. Order ID: ORD-98765."
                ),
                MessageTemplate(
                    TicketMessageRole.SUPPORT, supportUser,
                    "I've checked the payment verification system and found the issue. There was a sync delay " +
                        "with the blockchain node. I've manually triggered a re-verification and your payment " +
                        "is now confirmed. The order has been updated."
                ),
                MessageTemplate(
                    TicketMessageRole.USER, reporters[2],
                    "Confirmed — I can see the payment went through. Thanks!"
                )
            )
        )

        return 2
    }

    // --- Helper methods ---

    private fun saveReport(
        targetType: ReportTargetType,
        reason: ReportReason,
        targetTitle: String?,
        description: String?,
        status: ReportStatus,
        reporterId: ObjectId,
        targetId: ObjectId = ObjectId(),
        assigneeId: ObjectId? = null,
        resolution: ReportResolution? = null,
        resolvedBy: ObjectId? = null,
        resolvedAt: Long? = null,
        internalNotes: String? = null,
        siblingCount: Long = 1,
        createdAt: Long = Instant.now().toMicros(),
        updatedAt: Long? = null,
        timeline: List<Document> = emptyList()
    ): ObjectId {
        val doc = ReportDocument(
            targetId = targetId,
            targetType = targetType.shortName,
            contextId = null,
            reporterId = reporterId,
            reason = reason.shortName,
            description = description,
            status = status.shortName,
            targetTitle = targetTitle,
            targetSnapshot = null,
            assigneeId = assigneeId,
            resolution = resolution?.shortName,
            resolvedBy = resolvedBy,
            resolvedAt = resolvedAt,
            internalNotes = internalNotes,
            siblingCount = siblingCount,
            timeline = timeline,
            createdAt = createdAt,
            updatedAt = updatedAt ?: timeline.lastOrNull()?.getLong(ReportDocument.TL_FIELD_CREATED_AT) ?: createdAt
        )
        val saved = reportRepository.save(doc)
        return saved.id!!
    }

    private fun saveTicketWithMessages(
        subject: String,
        category: TicketCategory,
        priority: TicketPriority,
        status: TicketStatus,
        creatorId: ObjectId,
        assigneeId: ObjectId? = null,
        relatedReportIds: List<ObjectId> = emptyList(),
        createdAt: Long,
        resolvedAt: Long? = null,
        messages: List<MessageTemplate>
    ) {
        val messageInterval = HOUR_MICROS / 2
        var lastMessageAt: Long? = null

        val ticketId = ObjectId()

        messages.forEachIndexed { index, msg ->
            val msgCreatedAt = createdAt + (index + 1) * messageInterval
            lastMessageAt = msgCreatedAt

            ticketMessageRepository.save(
                TicketMessageDocument(
                    ticketId = ticketId,
                    authorId = msg.authorId,
                    authorRole = msg.role.shortName,
                    body = msg.body,
                    createdAt = msgCreatedAt
                )
            )
        }

        ticketRepository.save(
            TicketDocument(
                id = ticketId,
                subject = subject,
                category = category.shortName,
                status = status.shortName,
                priority = priority.shortName,
                priorityOrder = priority.sortOrder,
                creatorId = creatorId,
                assigneeId = assigneeId,
                relatedReportIds = relatedReportIds,
                messageCount = messages.size,
                lastMessageAt = lastMessageAt,
                resolvedAt = resolvedAt,
                createdAt = createdAt,
                updatedAt = lastMessageAt ?: createdAt
            )
        )
    }

    // --- Timeline entry builders ---

    private fun createdTimeline(reporterId: ObjectId, createdAt: Long): Document = Document()
        .append(ReportDocument.TL_FIELD_ID, UUIDv7.generate().toString())
        .append(ReportDocument.TL_FIELD_ACTION, ReportTimelineAction.CREATED.shortName)
        .append(ReportDocument.TL_FIELD_PERFORMED_BY, reporterId)
        .append(ReportDocument.TL_FIELD_DETAIL, null)
        .append(ReportDocument.TL_FIELD_CREATED_AT, createdAt)

    private fun assignedTimeline(supportUser: ObjectId, createdAt: Long): Document = Document()
        .append(ReportDocument.TL_FIELD_ID, UUIDv7.generate().toString())
        .append(ReportDocument.TL_FIELD_ACTION, ReportTimelineAction.ASSIGNED.shortName)
        .append(ReportDocument.TL_FIELD_PERFORMED_BY, supportUser)
        .append(ReportDocument.TL_FIELD_DETAIL, null)
        .append(ReportDocument.TL_FIELD_CREATED_AT, createdAt)

    private fun statusChangedTimeline(supportUser: ObjectId, createdAt: Long, detail: String): Document = Document()
        .append(ReportDocument.TL_FIELD_ID, UUIDv7.generate().toString())
        .append(ReportDocument.TL_FIELD_ACTION, ReportTimelineAction.STATUS_CHANGED.shortName)
        .append(ReportDocument.TL_FIELD_PERFORMED_BY, supportUser)
        .append(ReportDocument.TL_FIELD_DETAIL, detail)
        .append(ReportDocument.TL_FIELD_CREATED_AT, createdAt)

    private fun noteTimeline(supportUser: ObjectId, createdAt: Long, note: String): Document = Document()
        .append(ReportDocument.TL_FIELD_ID, UUIDv7.generate().toString())
        .append(ReportDocument.TL_FIELD_ACTION, ReportTimelineAction.NOTE_ADDED.shortName)
        .append(ReportDocument.TL_FIELD_PERFORMED_BY, supportUser)
        .append(ReportDocument.TL_FIELD_DETAIL, note)
        .append(ReportDocument.TL_FIELD_CREATED_AT, createdAt)

    private fun resolvedTimeline(supportUser: ObjectId, createdAt: Long, resolution: String): Document = Document()
        .append(ReportDocument.TL_FIELD_ID, UUIDv7.generate().toString())
        .append(ReportDocument.TL_FIELD_ACTION, ReportTimelineAction.RESOLVED.shortName)
        .append(ReportDocument.TL_FIELD_PERFORMED_BY, supportUser)
        .append(ReportDocument.TL_FIELD_DETAIL, resolution)
        .append(ReportDocument.TL_FIELD_CREATED_AT, createdAt)

    // --- User management ---

    private fun ensureUsers(): PlaygroundUsers {
        val allUsers = userRepository.findAll()
        val reporters = mutableListOf<ObjectId>()

        for (user in allUsers) {
            user.id?.let { reporters.add(it) }
        }

        val neededReporters = MIN_REPORTERS - reporters.size
        if (neededReporters > 0) {
            for (i in 1..neededReporters) {
                val username = "$REPORTER_USERNAME_PREFIX$i"
                val existing = userRepository.findOneByField("username", username)
                if (existing != null) {
                    reporters.add(existing.id!!)
                    continue
                }

                val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
                val user = User(
                    username = username,
                    displayName = "Reporter $i",
                    phone = "000000000",
                    email = "playground-reporter-$i@gimlee.com",
                    password = passwordHash,
                    passwordSalt = salt,
                    status = UserStatus.ACTIVE
                )
                val saved = userRepository.save(user)
                reporters.add(saved.id!!)
                log.debug("Created reporter user '{}'", username)
            }
        }

        val supportId = ensureSupportUser()
        val reporterIds = reporters.filter { it != supportId }.take(MIN_REPORTERS)

        return PlaygroundUsers(supportUser = supportId, reporters = reporterIds)
    }

    private fun ensureSupportUser(): ObjectId {
        val existing = userRepository.findOneByField("username", SUPPORT_USERNAME)
        if (existing != null) return existing.id!!

        val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
        val user = User(
            username = SUPPORT_USERNAME,
            displayName = "Support Agent",
            phone = "000000000",
            email = "playground-support@gimlee.com",
            password = passwordHash,
            passwordSalt = salt,
            status = UserStatus.ACTIVE
        )
        val saved = userRepository.save(user)
        log.debug("Created support user '{}'", SUPPORT_USERNAME)
        return saved.id!!
    }

    // --- Data classes ---

    private data class PlaygroundUsers(
        val supportUser: ObjectId,
        val reporters: List<ObjectId>
    )

    private data class ReportTemplate(
        val targetType: ReportTargetType,
        val reason: ReportReason,
        val targetTitle: String,
        val description: String?,
        val timeOffset: Long,
        val note: String? = null
    )

    private data class MessageTemplate(
        val role: TicketMessageRole,
        val authorId: ObjectId,
        val body: String
    )
}
