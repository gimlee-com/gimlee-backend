package com.gimlee.api.playground.ads.data

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Ad
import com.gimlee.ads.qa.domain.AnswerService
import com.gimlee.ads.qa.domain.QuestionService
import com.gimlee.ads.qa.domain.UpvoteService
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import kotlin.random.Random

@Profile("local", "dev", "test")
@Lazy(true)
@Component
class QaPopulator(
    private val adService: AdService,
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val upvoteService: UpvoteService,
    private val userRepository: UserRepository
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val MIN_QUESTIONS_PER_AD = 2
        private const val MAX_QUESTIONS_PER_AD = 6
        private const val SELLER_ANSWER_CHANCE = 0.7
        private const val COMMUNITY_ANSWER_CHANCE = 0.3
        private const val PIN_CHANCE = 0.3
        private const val UPVOTE_CHANCE = 0.4
        private const val BUYER_USERNAME_PREFIX = "qa_buyer_"
        private const val MIN_BUYER_USERS = 6
        private const val PASSWORD = "Password1"

        private val SAMPLE_QUESTIONS = listOf(
            "Does this come with a warranty?",
            "What's the estimated delivery time?",
            "Is this item available for international shipping?",
            "Can you provide more details about the condition?",
            "Do you accept returns if the item doesn't match the description?",
            "Is the price negotiable?",
            "How was this item stored?",
            "Are there any known defects or issues?",
            "Can I see additional photos?",
            "What payment methods do you accept besides crypto?",
            "Is this the latest version/model?",
            "Do you have multiple units available?",
            "What's the original purchase date?",
            "Does this include all original accessories?",
            "Can you ship this in discreet packaging?",
            "Is this compatible with standard specifications?",
            "How does the sizing run — true to size?",
            "What materials is this made from?",
            "Have you used this item yourself?",
            "Would you consider a trade?"
        )

        private val SAMPLE_SELLER_ANSWERS = listOf(
            "Yes, it comes with a 1-year manufacturer warranty. I can provide the warranty card upon request.",
            "Shipping usually takes 3-5 business days domestically. International orders may take 7-14 days depending on the destination.",
            "Yes, I ship internationally! Shipping costs vary by country — feel free to message me for a quote.",
            "The item is in excellent condition, barely used. I've included detailed photos showing all angles.",
            "Absolutely. I offer a 14-day return window if the item doesn't match the listing description.",
            "The listed price is fair for the condition, but I'm open to reasonable offers. Send me a message!",
            "Stored in a climate-controlled environment, away from direct sunlight and moisture.",
            "No known defects. Everything functions as expected. I've tested it thoroughly before listing.",
            "Sure! Message me directly and I'll send over additional high-resolution photos.",
            "Crypto is preferred, but we can discuss alternatives via the chat feature.",
            "Yes, this is the latest model released this year.",
            "I have 3 units available. Bulk discount possible if you're interested in more than one.",
            "Purchased 6 months ago. Still well within the warranty period.",
            "Yes, all original accessories, packaging, and documentation are included.",
            "Of course — all shipments are sent in plain, unmarked packaging for your privacy.",
            "Fully compatible. I've verified this myself before listing.",
            "Runs true to size based on my experience and buyer feedback.",
            "Made from premium materials — see the product description for the full specification.",
            "I used it briefly for testing purposes. It's essentially new.",
            "I'm primarily looking for a sale, but feel free to propose a trade via chat."
        )

        private val SAMPLE_COMMUNITY_ANSWERS = listOf(
            "I bought this exact item from the seller last month. Arrived in perfect condition and exactly as described!",
            "Can confirm the shipping was fast. Got mine in 4 days.",
            "I've been using mine for a few weeks now. Very happy with the quality.",
            "The seller was great to work with. Responded quickly to all my questions.",
            "Mine came with all accessories as promised. No complaints at all.",
            "Just a heads-up — make sure to verify the specs match your needs before ordering.",
            "I can vouch for this seller. Great communication throughout the process.",
            "The packaging was very secure when I received mine. No damage at all."
        )
    }

    fun populateQa() {
        val ads = adService.getFeaturedAds().content
        if (ads.isEmpty()) {
            log.warn("No active ads found. Run 'createAds' first. Skipping Q&A population.")
            return
        }

        val allUsers = ensureBuyerUsers(ads)
        log.info("Populating Q&A for {} ads with {} available users.", ads.size, allUsers.size)
        var totalQuestions = 0
        var totalSellerAnswers = 0
        var totalCommunityAnswers = 0

        for (ad in ads) {
            val stats = populateQaForAd(ad, allUsers)
            totalQuestions += stats.questions
            totalSellerAnswers += stats.sellerAnswers
            totalCommunityAnswers += stats.communityAnswers
        }

        log.info(
            "Q&A population complete: {} questions, {} seller answers, {} community answers across {} ads.",
            totalQuestions, totalSellerAnswers, totalCommunityAnswers, ads.size
        )
    }

    private fun ensureBuyerUsers(ads: List<Ad>): List<User> {
        val allUsers = userRepository.findAll()
        val adOwnerIds = ads.map { it.userId }.toSet()
        val nonOwnerCount = allUsers.count { it.id?.toHexString() !in adOwnerIds }

        if (nonOwnerCount >= MIN_BUYER_USERS) return allUsers

        val needed = MIN_BUYER_USERS - nonOwnerCount
        log.info("Only {} non-owner users found. Creating {} buyer users for Q&A population.", nonOwnerCount, needed)

        val createdUsers = (1..needed).mapNotNull { i ->
            val username = "$BUYER_USERNAME_PREFIX$i"
            val existing = userRepository.findOneByField("username", username)
            if (existing != null) return@mapNotNull existing

            val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
            val user = User(
                username = username,
                displayName = "QA Buyer $i",
                phone = "000000000",
                email = "qa-buyer-$i@gimlee.com",
                password = passwordHash,
                passwordSalt = salt,
                status = UserStatus.ACTIVE
            )
            userRepository.save(user).also { log.debug("Created buyer user '{}'", username) }
        }

        return allUsers + createdUsers
    }

    private fun populateQaForAd(ad: Ad, allUsers: List<User>): PopulationStats {
        var questions = 0
        var sellerAnswers = 0
        var communityAnswers = 0

        try {
            val nonOwnerUsers = allUsers.filter { it.id?.toHexString() != ad.userId }.shuffled()
            if (nonOwnerUsers.isEmpty()) return PopulationStats()

            val numberOfQuestions = Random.nextInt(MIN_QUESTIONS_PER_AD, MAX_QUESTIONS_PER_AD + 1)
            val questionsToAsk = SAMPLE_QUESTIONS.shuffled().take(numberOfQuestions)

            for ((index, questionText) in questionsToAsk.withIndex()) {
                val asker = nonOwnerUsers[index % nonOwnerUsers.size]
                val askerId = asker.id?.toHexString() ?: continue

                val (_, question) = questionService.askQuestion(ad.id, askerId, ad.userId, questionText)
                if (question == null) continue
                questions++

                if (Random.nextDouble() < SELLER_ANSWER_CHANCE) {
                    val answerText = SAMPLE_SELLER_ANSWERS[index % SAMPLE_SELLER_ANSWERS.size]
                    val (_, answer) = answerService.submitSellerAnswer(question.id, ad.userId, answerText)
                    if (answer != null) {
                        sellerAnswers++
                        if (Random.nextDouble() < PIN_CHANCE) {
                            questionService.togglePin(question.id, ad.id)
                        }
                    }
                }

                if (Random.nextDouble() < COMMUNITY_ANSWER_CHANCE) {
                    val communityUser = nonOwnerUsers.firstOrNull { it.id?.toHexString() != askerId }
                    val communityUserId = communityUser?.id?.toHexString()
                    if (communityUserId != null) {
                        val communityText = SAMPLE_COMMUNITY_ANSWERS.random()
                        val (_, communityAnswer) = answerService.submitCommunityAnswer(question.id, communityUserId, communityText)
                        if (communityAnswer != null) communityAnswers++
                    }
                }

                addRandomUpvotes(question.id, ad.userId, nonOwnerUsers)
            }
        } catch (e: Exception) {
            log.warn("Error populating Q&A for ad {}: {}", ad.id, e.message)
        }

        return PopulationStats(questions, sellerAnswers, communityAnswers)
    }

    private fun addRandomUpvotes(questionId: String, sellerId: String, nonOwnerUsers: List<User>) {
        nonOwnerUsers
            .filter { Random.nextDouble() < UPVOTE_CHANCE }
            .forEach { voter ->
                val voterId = voter.id?.toHexString() ?: return@forEach
                upvoteService.toggleUpvote(questionId, voterId, sellerId)
            }
    }

    private data class PopulationStats(
        val questions: Int = 0,
        val sellerAnswers: Int = 0,
        val communityAnswers: Int = 0
    )
}
