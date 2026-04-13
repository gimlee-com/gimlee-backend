package com.gimlee.notifications.domain.model

import com.gimlee.notifications.domain.model.NotificationCategory.*
import com.gimlee.notifications.domain.model.NotificationSeverity.*

enum class NotificationType(
    val slug: String,
    val category: NotificationCategory,
    val defaultSeverity: NotificationSeverity,
    val baseKey: String
) {
    // Orders & Payments
    ORDER_NEW("order.new", ORDERS, INFO, "gimlee.notifications.order.new"),
    ORDER_AWAITING_PAYMENT("order.awaiting_payment", ORDERS, INFO, "gimlee.notifications.order.awaiting-payment"),
    ORDER_COMPLETE("order.complete", ORDERS, SUCCESS, "gimlee.notifications.order.complete"),
    ORDER_OVERPAID("order.overpaid", ORDERS, WARNING, "gimlee.notifications.order.overpaid"),
    ORDER_PAYMENT_TIMEOUT("order.payment_timeout", ORDERS, DANGER, "gimlee.notifications.order.payment-timeout"),
    ORDER_UNDERPAID("order.underpaid", ORDERS, DANGER, "gimlee.notifications.order.underpaid"),
    ORDER_CANCELLED("order.cancelled", ORDERS, WARNING, "gimlee.notifications.order.cancelled"),
    ORDER_PAYMENT_DEADLINE("order.payment_deadline", ORDERS, DANGER, "gimlee.notifications.order.payment-deadline"),

    // Chat
    CHAT_NEW_MESSAGE("chat.new_message", MESSAGES, INFO, "gimlee.notifications.chat.new-message"),
    CHAT_NEW_CONVERSATION("chat.new_conversation", MESSAGES, INFO, "gimlee.notifications.chat.new-conversation"),

    // Ads & Marketplace
    AD_STOCK_DEPLETED("ad.stock_depleted", ADS, DANGER, "gimlee.notifications.ad.stock-depleted"),
    AD_STOCK_LOW("ad.stock_low", ADS, WARNING, "gimlee.notifications.ad.stock-low"),
    AD_CATEGORY_HIDDEN("ad.category_hidden", ADS, WARNING, "gimlee.notifications.ad.category-hidden"),
    AD_WATCHLIST_PRICE_CHANGE("ad.watchlist_price_change", ADS, INFO, "gimlee.notifications.ad.watchlist-price-change"),
    AD_WATCHLIST_BACK_IN_STOCK("ad.watchlist_back_in_stock", ADS, SUCCESS, "gimlee.notifications.ad.watchlist-back-in-stock"),
    AD_WATCHLIST_DEACTIVATED("ad.watchlist_deactivated", ADS, INFO, "gimlee.notifications.ad.watchlist-deactivated"),

    // Q&A
    QA_NEW_QUESTION("qa.new_question", QA, INFO, "gimlee.notifications.qa.new-question"),
    QA_NEW_ANSWER("qa.new_answer", QA, INFO, "gimlee.notifications.qa.new-answer"),
    QA_UPVOTE_MILESTONE("qa.upvote_milestone", QA, SUCCESS, "gimlee.notifications.qa.upvote-milestone"),

    // Support
    TICKET_REPLY("ticket.reply", SUPPORT, INFO, "gimlee.notifications.ticket.reply"),
    TICKET_STATUS_CHANGE("ticket.status_change", SUPPORT, INFO, "gimlee.notifications.ticket.status-change"),
    TICKET_AWAITING_USER("ticket.awaiting_user", SUPPORT, WARNING, "gimlee.notifications.ticket.awaiting-user"),
    REPORT_RESOLVED("report.resolved", SUPPORT, SUCCESS, "gimlee.notifications.report.resolved"),

    // Account
    ACCOUNT_BAN("account.ban", ACCOUNT, DANGER, "gimlee.notifications.account.ban"),
    ACCOUNT_UNBAN("account.unban", ACCOUNT, SUCCESS, "gimlee.notifications.account.unban"),
    ACCOUNT_BAN_EXPIRING("account.ban_expiring", ACCOUNT, INFO, "gimlee.notifications.account.ban-expiring"),
    MODERATION_WARNING("moderation.warning", ACCOUNT, WARNING, "gimlee.notifications.moderation.warning"),
    MODERATION_CONTENT_REMOVED("moderation.content_removed", ACCOUNT, WARNING, "gimlee.notifications.moderation.content-removed"),
    SYSTEM_VIEWING_KEY_VERIFIED("system.viewing_key_verified", ACCOUNT, SUCCESS, "gimlee.notifications.system.viewing-key-verified"),
    ACCOUNT_WELCOME("account.welcome", ACCOUNT, SUCCESS, "gimlee.notifications.account.welcome");

    val titleKey: String get() = "$baseKey.title"
    val messageKeyTemplate: String get() = "$baseKey.message"

    companion object {
        private val slugMap = entries.associateBy { it.slug }
        fun fromSlug(slug: String): NotificationType =
            slugMap[slug] ?: throw IllegalArgumentException("Unknown NotificationType slug: $slug")
    }
}
