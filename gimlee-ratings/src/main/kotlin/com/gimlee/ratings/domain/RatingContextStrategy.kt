package com.gimlee.ratings.domain

import com.gimlee.ratings.domain.model.RepKind
import java.time.Duration

interface RatingContextStrategy {
    fun supports(contextType: String): Boolean
    fun isReciprocal(): Boolean = true
    fun dwellTime(): Duration
    fun ratingWindow(): Duration
    fun editWindow(): Duration
    fun supplementCooldown(): Duration
    fun maxSupplements(): Int
    fun repKindForRater(raterRole: String): RepKind
}
