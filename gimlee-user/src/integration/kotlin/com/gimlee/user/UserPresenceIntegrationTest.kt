package com.gimlee.user

import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.toMicros
import com.gimlee.events.UserActivityEvent
import com.gimlee.user.domain.UserPresenceService
import com.gimlee.user.domain.model.UserPresenceStatus
import com.gimlee.user.persistence.UserPresenceRepository
import com.gimlee.user.persistence.model.UserPresenceDocument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

class UserPresenceIntegrationTest(
    private val userPresenceService: UserPresenceService,
    private val userPresenceRepository: UserPresenceRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val mongoTemplate: MongoTemplate
) : BaseIntegrationTest({

    Given("a user id") {
        mongoTemplate.dropCollection(UserPresenceDocument.COLLECTION_NAME)
        val userId = ObjectId.get().toHexString()

        When("tracking activity for the first time") {
            userPresenceService.trackActivity(userId)
            
            Then("the user should be ONLINE inferred from buffer") {
                val presence = userPresenceService.getUserPresence(userId)
                presence.status shouldBe UserPresenceStatus.ONLINE
            }
        }

        When("flushing the buffer") {
            userPresenceService.trackActivity(userId)
            userPresenceService.flushBuffer()

            Then("the activity should be saved to the database") {
                val doc = userPresenceRepository.findByUserId(userId)
                doc shouldNotBe null
                doc?.lastSeenAt shouldNotBe 0L
            }
        }

        When("a user manually sets status to AWAY") {
            userPresenceService.updateStatus(userId, UserPresenceStatus.AWAY, "Gone fishing")

            Then("the status should be AWAY") {
                val presence = userPresenceService.getUserPresence(userId)
                presence.status shouldBe UserPresenceStatus.AWAY
                presence.customStatus shouldBe "Gone fishing"
            }
        }

        When("the user was seen a long time ago") {
            val longAgo = Instant.now().minus(10, ChronoUnit.MINUTES)
            val doc = UserPresenceDocument(
                userId = ObjectId(userId),
                lastSeenAt = longAgo.toMicros(),
                status = UserPresenceStatus.ONLINE.shortName,
                customStatus = null
            )
            userPresenceRepository.save(doc)

            Then("the inferred status should be OFFLINE") {
                val presence = userPresenceService.getUserPresence(userId)
                presence.status shouldBe UserPresenceStatus.OFFLINE
            }
        }

        When("the user is AWAY but was seen a long time ago") {
            val longAgo = Instant.now().minus(10, ChronoUnit.MINUTES)
            val doc = UserPresenceDocument(
                userId = ObjectId(userId),
                lastSeenAt = longAgo.toMicros(),
                status = UserPresenceStatus.AWAY.shortName,
                customStatus = "Still away"
            )
            userPresenceRepository.save(doc)

            Then("the status should be OFFLINE (offline takes precedence over manual status)") {
                val presence = userPresenceService.getUserPresence(userId)
                presence.status shouldBe UserPresenceStatus.OFFLINE
            }
        }
        
        When("an UserActivityEvent is published for a new user") {
            val newUserId = ObjectId.get().toHexString()
            eventPublisher.publishEvent(UserActivityEvent(newUserId))
            
            Then("the service should track the activity") {
                val presence = userPresenceService.getUserPresence(newUserId)
                presence.status shouldBe UserPresenceStatus.ONLINE
            }
        }
    }
})
