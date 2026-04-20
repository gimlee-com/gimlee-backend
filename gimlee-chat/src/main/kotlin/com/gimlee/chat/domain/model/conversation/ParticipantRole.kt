package com.gimlee.chat.domain.model.conversation

enum class ParticipantRole(val shortName: String) {
    OWNER("OWN"),
    ADMIN("ADM"),
    MODERATOR("MOD"),
    MEMBER("MBR");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): ParticipantRole =
            map[shortName] ?: throw IllegalArgumentException("Unknown ParticipantRole: $shortName")
    }
}
