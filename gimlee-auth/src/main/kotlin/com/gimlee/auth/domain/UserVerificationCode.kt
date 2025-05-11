package com.gimlee.auth.domain

import org.bson.types.ObjectId
import java.time.LocalDateTime

data class UserVerificationCode(
    val userId: ObjectId,
    val verificationCode: String,
    val issuedAt: LocalDateTime
) {
    companion object {
        const val FIELD_USERID = "userId"
        const val FIELD_VERIFICATION_CODE = "verificationCode"
        const val FIELD_ISSUED_AT = "issuedAt"
    }
}