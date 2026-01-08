package com.gimlee.user.domain.model

data class UserProfile(
    val userId: String,
    val avatarUrl: String?,
    val updatedAt: Long
)
