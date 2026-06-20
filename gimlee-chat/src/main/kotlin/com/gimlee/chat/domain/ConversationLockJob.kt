package com.gimlee.chat.domain

import com.gimlee.chat.ChatProperties
import com.gimlee.chat.web.ChatEventBroadcaster
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ConversationLockJob(
    private val conversationService: ConversationService,
    private val chatEventBroadcaster: ChatEventBroadcaster,
    private val properties: ChatProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gimlee.chat.lock-sweep-interval-ms:60000}")
    fun sweepExpiredLocks() {
        val expired = conversationService.findExpiredLocks(properties.lockSweepBatchSize)
        if (expired.isEmpty()) return

        log.info("Processing {} expired conversation locks", expired.size)

        var successCount = 0
        for (conversation in expired) {
            try {
                val locked = conversationService.lockConversation(conversation.id)
                if (locked) {
                    log.info("Automatically locked conversation {} due to expiration", conversation.id)
                    chatEventBroadcaster.closeEmittersForConversation(conversation.id)
                    successCount++
                }
            } catch (e: Exception) {
                log.error("Failed to automatically lock conversation {}", conversation.id, e)
            }
        }

        if (successCount > 0) {
            log.info("Successfully locked {}/{} expired conversations", successCount, expired.size)
        }
    }
}
