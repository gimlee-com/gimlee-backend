package com.gimlee.chat

import com.gimlee.chat.domain.ConversationLockJob
import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import java.time.temporal.ChronoUnit

class ConversationLockIntegrationTest(
    private val conversationService: ConversationService,
    private val conversationLockJob: ConversationLockJob
) : BaseIntegrationTest({

    Given("an active conversation with an expired auto-lock") {
        val (conversation, _) = conversationService.createConversation(
            type = "TEST",
            participantUserIds = listOf("user1", "user2")
        )
        conversation.id shouldNotBe ""

        val past = Instant.now().minus(1, ChronoUnit.HOURS)
        conversationService.setAutoLockAt(conversation.id, past)
        
        When("the lock sweeper job runs") {
            conversationLockJob.sweepExpiredLocks()

            Then("the conversation should be LOCKED") {
                val updated = conversationService.findById(conversation.id)!!
                updated.status shouldBe ConversationStatus.LOCKED
                updated.autoLockAt shouldBe null
            }
        }
    }

    Given("an active conversation with a future auto-lock") {
        val (conversation, _) = conversationService.createConversation(
            type = "TEST",
            participantUserIds = listOf("user3", "user4")
        )

        val future = Instant.now().plus(1, ChronoUnit.HOURS)
        conversationService.setAutoLockAt(conversation.id, future)

        When("the lock sweeper job runs") {
            conversationLockJob.sweepExpiredLocks()

            Then("the conversation should still be ACTIVE") {
                val updated = conversationService.findById(conversation.id)!!
                updated.status shouldBe ConversationStatus.ACTIVE
                updated.autoLockAt?.toMicros() shouldBe future.toMicros()
            }
        }
    }
})
