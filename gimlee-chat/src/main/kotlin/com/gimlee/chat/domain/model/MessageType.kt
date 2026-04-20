package com.gimlee.chat.domain.model

enum class MessageType(val shortName: String) {
    REGULAR("R"),
    SYSTEM("S");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): MessageType =
            map[shortName] ?: throw IllegalArgumentException("Unknown MessageType: $shortName")
    }
}
