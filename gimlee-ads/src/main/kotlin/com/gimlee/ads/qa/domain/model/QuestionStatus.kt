package com.gimlee.ads.qa.domain.model

enum class QuestionStatus(val shortName: String) {
    PENDING("P"),
    ANSWERED("A"),
    HIDDEN("H"),
    REMOVED("R");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): QuestionStatus =
            map[sn] ?: throw IllegalArgumentException("Unknown QuestionStatus: $sn")
    }
}
