package com.gimlee.ads.qa.domain.model

enum class AnswerType(val shortName: String) {
    SELLER("S"),
    COMMUNITY("C");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): AnswerType =
            map[sn] ?: throw IllegalArgumentException("Unknown AnswerType: $sn")
    }
}
