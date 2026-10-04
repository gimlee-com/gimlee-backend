package com.gimlee.chat.domain.model

enum class ChatEventType(val shortName: String) {
    MESSAGE("M"),
    TYPING_INDICATOR("TI"),
    CONVERSATION_LOCKED("CL");

    companion object {
        fun fromShortName(shortName: String): ChatEventType {
            return entries.find { it.shortName == shortName }
                ?: throw IllegalArgumentException("Unknown ChatEventType shortName: $shortName")
        }
    }
}
