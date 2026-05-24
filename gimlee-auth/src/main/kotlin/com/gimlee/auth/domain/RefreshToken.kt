package com.gimlee.auth.domain

import java.util.UUID

data class RefreshToken(
    val id: UUID,
    val userId: String,
    val familyId: UUID,
    val hashedToken: String,
    val deviceId: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val revoked: Boolean = false,
    val revokedAt: Long? = null
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_USER_ID = "uid"
        const val FIELD_FAMILY_ID = "fam"
        const val FIELD_HASHED_TOKEN = "tkn"
        const val FIELD_DEVICE_ID = "dev"
        const val FIELD_ISSUED_AT = "iat"
        const val FIELD_EXPIRES_AT = "exp"
        const val FIELD_REVOKED = "rev"
        const val FIELD_REVOKED_AT = "rAt"
    }
}
