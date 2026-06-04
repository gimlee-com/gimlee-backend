package com.gimlee.ratings.domain.model

enum class SubjectKind(val shortName: String) {
    USER("USER");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): SubjectKind =
            map[shortName] ?: throw IllegalArgumentException("Unknown SubjectKind: $shortName")
    }
}
