package com.gimlee.auth.domain

import org.bson.types.ObjectId
import java.time.LocalDateTime

data class User(
    val id: ObjectId? = null,
    val username: String? = "",
    val displayName: String? = "",
    val email: String? = "",
    val phone: String? = "",
    val password: String? = "",
    val passwordSalt: String? = "",
    val lastLogin: LocalDateTime? = null,
    val status: UserStatus? = null
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USERNAME = "username"
        const val FIELD_EMAIL = "email"
        const val FIELD_PASSWORD = "password"
        const val FIELD_PASSWORD_SALT = "passwordSalt"
        const val FIELD_PHONE = "phone"
        const val FIELD_VERIFICATION_CODE = "verificationCode"
    }
}