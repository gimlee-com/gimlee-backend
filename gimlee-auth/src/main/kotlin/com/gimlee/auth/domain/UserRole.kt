package com.gimlee.auth.domain

import org.bson.types.ObjectId
import com.gimlee.auth.model.Role

data class UserRole(
    val userId: ObjectId,
    val role: Role
) {
    companion object {
        const val FIELD_USERID = "userId"
        const val FIELD_ROLE = "role"
    }
}