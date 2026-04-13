package com.gimlee.payments.domain

import com.gimlee.common.toMicros
import com.gimlee.events.PaymentDeadlineApproachingEvent
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.persistence.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class PaymentDeadlineReminderJob(
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentProperties: PaymentProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gimlee.payments.deadline-reminder.check-interval-ms:60000}")
    fun checkApproachingDeadlines() {
        val now = Instant.now()
        val windowMinutes = paymentProperties.deadlineReminder.windowMinutes
        val ceiling = now.plus(windowMinutes, ChronoUnit.MINUTES)

        val candidates = paymentRepository.findApproachingDeadlines(now.toMicros(), ceiling.toMicros())
        if (candidates.isEmpty()) return

        log.debug("Found {} payments approaching deadline within {} min", candidates.size, windowMinutes)

        for (payment in candidates) {
            if (paymentRepository.markReminderSent(payment.id)) {
                eventPublisher.publishEvent(
                    PaymentDeadlineApproachingEvent(
                        purchaseId = payment.purchaseId,
                        buyerId = payment.buyerId,
                        sellerId = payment.sellerId,
                        amount = payment.amount,
                        deadline = payment.deadline
                    )
                )
                log.debug("Published deadline approaching event: purchaseId={}", payment.purchaseId)
            }
        }
    }
}
