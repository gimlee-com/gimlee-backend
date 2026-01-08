package com.gimlee.user.domain

import com.gimlee.common.toMicros
import com.gimlee.user.domain.model.UserProfile
import com.gimlee.user.persistence.ProfileRepository
import com.gimlee.user.persistence.model.UserProfileDocument
import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ProfileService(private val profileRepository: ProfileRepository) {

    fun getProfile(userId: String): UserProfile? {
        return profileRepository.findByUserId(ObjectId(userId))?.toDomain()
    }

    fun updateAvatar(userId: String, avatarUrl: String): UserProfile {
        val updatedAt = Instant.now().toMicros()
        val profile = UserProfile(
            userId = userId,
            avatarUrl = avatarUrl,
            updatedAt = updatedAt
        )

        profileRepository.save(UserProfileDocument.fromDomain(profile))
        return profile
    }
}
