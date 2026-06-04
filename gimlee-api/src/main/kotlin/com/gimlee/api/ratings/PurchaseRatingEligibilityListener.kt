package com.gimlee.api.ratings

import com.gimlee.ads.persistence.AdRepository
import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.ratings.domain.RatingEligibilityService
import com.gimlee.ratings.domain.model.RatingSubjectSnapshot
import com.gimlee.ratings.domain.model.RatingSnapshotItem
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PurchaseRatingEligibilityListener(
    private val ratingEligibilityService: RatingEligibilityService,
    private val adRepository: AdRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CONTEXT_TYPE_ORDER = "ORDER"
    }

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        if (event.status != PurchaseStatus.COMPLETE.id) return

        val purchaseId = event.purchaseId.toHexString()
        val buyerId = event.buyerId.toHexString()
        val sellerId = event.sellerId.toHexString()

        val adIds = event.items.map { it.adId }
        val ads = adRepository.findAllByIds(adIds).associateBy { it.id }

        val snapshotItems = event.items.mapNotNull { item ->
            val ad = ads[item.adId] ?: return@mapNotNull null
            RatingSnapshotItem(
                adId = ad.id.toHexString(),
                name = ad.title,
                quantity = item.quantity,
                unitPrice = ad.price?.toPlainString() ?: "0",
                currency = ad.currency?.name ?: ""
            )
        }

        val snapshot = RatingSubjectSnapshot(
            refType = "PURCHASE_ITEMS",
            items = snapshotItems
        )

        val strategy = ratingEligibilityService.getStrategy(CONTEXT_TYPE_ORDER)

        val buyerGrant = ratingEligibilityService.grant(
            contextType = CONTEXT_TYPE_ORDER,
            contextId = purchaseId,
            raterId = buyerId,
            rateeId = sellerId,
            repKind = strategy.repKindForRater("BUYER"),
            snapshot = snapshot,
            completedAt = event.timestamp,
            dwellTime = strategy.dwellTime(),
            ratingWindow = strategy.ratingWindow()
        )

        if (buyerGrant != null) {
            log.info("Granted buyer→seller rating eligibility for purchase {}", purchaseId)
        } else {
            log.warn("Failed to grant buyer→seller rating eligibility for purchase {} (duplicate?)", purchaseId)
        }

        if (strategy.isReciprocal()) {
            val sellerGrant = ratingEligibilityService.grant(
                contextType = CONTEXT_TYPE_ORDER,
                contextId = purchaseId,
                raterId = sellerId,
                rateeId = buyerId,
                repKind = strategy.repKindForRater("SELLER"),
                snapshot = snapshot,
                completedAt = event.timestamp,
                dwellTime = strategy.dwellTime(),
                ratingWindow = strategy.ratingWindow()
            )

            if (sellerGrant != null) {
                log.info("Granted seller→buyer rating eligibility for purchase {}", purchaseId)
            } else {
                log.warn("Failed to grant seller→buyer rating eligibility for purchase {} (duplicate?)", purchaseId)
            }
        }
    }
}
