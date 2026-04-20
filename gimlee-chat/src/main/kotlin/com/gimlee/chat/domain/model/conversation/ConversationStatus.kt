package com.gimlee.chat.domain.model.conversation

enum class ConversationStatus(val shortName: String) {
    ACTIVE("ACT"),
    LOCKED("LCK"),
    ARCHIVED("ARC");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): ConversationStatus =
            map[shortName] ?: throw IllegalArgumentException("Unknown ConversationStatus: $shortName")
    }
}
