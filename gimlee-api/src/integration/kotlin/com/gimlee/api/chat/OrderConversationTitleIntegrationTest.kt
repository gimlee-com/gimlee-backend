package com.gimlee.api.chat

import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.purchases.persistence.PurchaseRepository
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.domain.model.StatusChange
import com.gimlee.chat.domain.ConversationTitleService
import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationParticipant
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.purchases.domain.model.DeliveryAddressSnapshot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

class OrderConversationTitleIntegrationTest(
    private val titleService: ConversationTitleService,
    private val purchaseRepository: PurchaseRepository,
    private val userRepository: UserRepository
) : BaseIntegrationTest({

    Given("a seller and a buyer") {
        val sellerId = ObjectId.get()
        val sellerName = "seller_jack"
        userRepository.save(User(id = sellerId, username = sellerName))

        val buyerId = ObjectId.get()
        val buyerName = "buyer_jill"
        userRepository.save(User(id = buyerId, username = buyerName))

        val purchase = Purchase(
            id = ObjectId.get(),
            buyerId = buyerId,
            sellerId = sellerId,
            items = emptyList(),
            totalAmount = BigDecimal.ZERO,
            status = PurchaseStatus.CREATED,
            deliveryAddress = DeliveryAddressSnapshot(
                name = "Home",
                fullName = "Jill Doe",
                street = "123 Main St",
                city = "Warsaw",
                postalCode = "00-001",
                country = "PL",
                phoneNumber = "+48123456789"
            ),
            statusHistory = listOf(StatusChange(PurchaseStatus.CREATED, Instant.now())),
            createdAt = Instant.now()
        )
        purchaseRepository.save(purchase)

        val conversation = Conversation(
            id = ObjectId.get().toHexString(),
            type = ConversationTypes.ORDER,
            participants = listOf(
                ConversationParticipant(sellerId.toHexString(), ParticipantRole.MEMBER, Instant.now()),
                ConversationParticipant(buyerId.toHexString(), ParticipantRole.MEMBER, Instant.now())
            ),
            linkType = ConversationLinkTypes.PURCHASE,
            linkId = purchase.id.toHexString(),
            status = ConversationStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            lastActivityAt = Instant.now()
        )

        When("the buyer views the conversation title") {
            val titles = titleService.resolveTitles(listOf(conversation), buyerId.toHexString(), Locale.ENGLISH)
            val title = titles[conversation.id]!!

            Then("it should show the seller's name") {
                title shouldContain sellerName
                title shouldNotContain buyerName
            }
        }

        When("the seller views the conversation title") {
            val titles = titleService.resolveTitles(listOf(conversation), sellerId.toHexString(), Locale.ENGLISH)
            val title = titles[conversation.id]!!

            Then("it should show the buyer's name") {
                title shouldContain buyerName
                title shouldNotContain sellerName
            }
        }
        
        When("checking title length") {
            val titles = titleService.resolveTitles(listOf(conversation), buyerId.toHexString(), Locale.ENGLISH)
            val title = titles[conversation.id]!!
            
            Then("it should not be excessively long") {
                // Truncated ID is 8 chars.
                // "Order 12345678 with seller_jack on 2026-06-21 08:49"
                // Length: 6 + 8 + 6 + 11 + 4 + 16 = 51
                title.length shouldBe 51
            }
        }
    }

    Given("a conversation with many participants") {
        val currentUserId = ObjectId.get().toHexString()
        val participants = (1..5).map { i ->
            val id = ObjectId.get()
            userRepository.save(User(id = id, username = "user$i"))
            ConversationParticipant(id.toHexString(), ParticipantRole.MEMBER, Instant.now())
        } + ConversationParticipant(currentUserId, ParticipantRole.MEMBER, Instant.now())

        val conversation = Conversation(
            id = ObjectId.get().toHexString(),
            type = "GROUP",
            participants = participants,
            status = ConversationStatus.ACTIVE,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            lastActivityAt = Instant.now()
        )

        When("resolving title") {
            val titles = titleService.resolveTitles(listOf(conversation), currentUserId, Locale.ENGLISH)
            val title = titles[conversation.id]!!

            Then("it should be truncated") {
                title shouldContain "user1, user2, user3"
                title shouldContain "and 2 others"
            }
        }
    }
})
