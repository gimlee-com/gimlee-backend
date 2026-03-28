package com.gimlee.notifications.event

import com.gimlee.events.UserBannedEvent
import com.gimlee.notifications.email.EmailService
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.event.EventListener
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.InputStreamReader
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class BanNotificationListener(
    private val emailService: EmailService,
    private val resourceLoader: ResourceLoader,
    private val messageSource: MessageSource
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val banEmailTemplate: Mustache = DefaultMustacheFactory().compile(
        InputStreamReader(
            resourceLoader.getResource("classpath:email/ban-notification.mustache").inputStream,
            Charsets.UTF_8
        ),
        "banEmailTemplate"
    )

    @Async
    @EventListener
    fun handleUserBanned(event: UserBannedEvent) {
        if (event.email.isBlank()) {
            log.warn("Cannot send ban notification — no email for user {}", event.userId)
            return
        }

        try {
            val locale = LocaleContextHolder.getLocale()
            val data = buildEmailData(event, locale)
            val html = StringWriter().also { banEmailTemplate.execute(it, data).flush() }.toString()

            emailService.sendEmail(event.email, data["title"]!!, html)
            log.info("Ban notification sent to user {} at {}", event.userId, event.email)
        } catch (e: Exception) {
            log.error("Failed to send ban notification to user {}: {}", event.userId, e.message, e)
        }
    }

    private fun buildEmailData(event: UserBannedEvent, locale: java.util.Locale): Map<String, String?> {
        val msg = { key: String, args: Array<Any>? -> messageSource.getMessage(key, args, locale) }
        val prefix = "gimlee.notifications.ban.email"

        val banUntilText = event.bannedUntil?.let {
            val formatted = Instant.ofEpochMilli(it / 1000)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))
            msg("$prefix.ban-until", arrayOf(formatted))
        }

        return mapOf(
            "title" to msg("$prefix.title", null),
            "heading" to msg("$prefix.heading", null),
            "greeting" to msg("$prefix.greeting", arrayOf(event.username)),
            "body" to msg("$prefix.body", null),
            "reasonLabel" to msg("$prefix.reason-label", null),
            "reason" to event.reason,
            "banUntilText" to banUntilText,
            "contact" to msg("$prefix.contact", null),
            "sentBy" to msg("$prefix.sent-by", null)
        )
    }
}
