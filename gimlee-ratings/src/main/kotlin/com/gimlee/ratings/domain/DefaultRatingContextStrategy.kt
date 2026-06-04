package com.gimlee.ratings.domain

import com.gimlee.ratings.domain.model.RepKind
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Order(Int.MAX_VALUE)
class DefaultRatingContextStrategy(
    @Value("\${gimlee.ratings.edit-window-minutes:30}") private val editWindowMinutes: Long,
    @Value("\${gimlee.ratings.supplement-cooldown-days:7}") private val supplementCooldownDays: Long,
    @Value("\${gimlee.ratings.max-supplements:4}") private val maxSupplementsValue: Int,
    @Value("\${gimlee.ratings.eligibility.dwell-days:7}") private val dwellDays: Long,
    @Value("\${gimlee.ratings.eligibility.window-days:60}") private val windowDays: Long
) : RatingContextStrategy {

    override fun supports(contextType: String): Boolean = true

    override fun dwellTime(): Duration = Duration.ofDays(dwellDays)

    override fun ratingWindow(): Duration = Duration.ofDays(windowDays)

    override fun editWindow(): Duration = Duration.ofMinutes(editWindowMinutes)

    override fun supplementCooldown(): Duration = Duration.ofDays(supplementCooldownDays)

    override fun maxSupplements(): Int = maxSupplementsValue

    override fun repKindForRater(raterRole: String): RepKind =
        when (raterRole.uppercase()) {
            "BUYER" -> RepKind.SELLER
            "SELLER" -> RepKind.BUYER
            else -> RepKind.SELLER
        }
}
