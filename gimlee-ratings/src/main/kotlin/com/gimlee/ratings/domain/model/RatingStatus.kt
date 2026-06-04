package com.gimlee.ratings.domain.model

enum class RatingStatus(val shortName: String) {
    PUBLISHED("PUB"),
    HIDDEN("HID"),
    DELETED("DEL");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): RatingStatus =
            map[shortName] ?: throw IllegalArgumentException("Unknown RatingStatus: $shortName")
    }
}
