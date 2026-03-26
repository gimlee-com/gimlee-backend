package com.gimlee.auth.domain

data class UserBan(
    val id: String,
    val userId: String,
    val reason: String,
    val bannedBy: String,
    val bannedAt: Long,
    val bannedUntil: Long?,
    val unbannedBy: String?,
    val unbannedAt: Long?,
    val active: Boolean
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_REASON = "rsn"
        const val FIELD_BANNED_BY = "bb"
        const val FIELD_BANNED_AT = "ba"
        const val FIELD_BANNED_UNTIL = "bu"
        const val FIELD_UNBANNED_BY = "ub"
        const val FIELD_UNBANNED_AT = "ua"
        const val FIELD_ACTIVE = "act"
    }
}
