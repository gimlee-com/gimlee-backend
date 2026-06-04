package com.gimlee.ratings.domain.model

enum class EligibilityStatus(val shortName: String) {
    PENDING("PND"),
    CONSUMED("CSD");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): EligibilityStatus =
            map[shortName] ?: throw IllegalArgumentException("Unknown EligibilityStatus: $shortName")
    }
}
