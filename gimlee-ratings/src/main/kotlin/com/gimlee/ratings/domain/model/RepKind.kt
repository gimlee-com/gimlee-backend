package com.gimlee.ratings.domain.model

enum class RepKind(val shortName: String) {
    SELLER("SEL"),
    BUYER("BUY");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): RepKind =
            map[shortName] ?: throw IllegalArgumentException("Unknown RepKind: $shortName")
    }
}
