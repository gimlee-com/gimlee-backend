package com.gimlee.auth.service

import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import org.bson.types.ObjectId
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository) {

    fun findById(id: String): User? {
        return try {
            userRepository.findOneByField(User.FIELD_ID, ObjectId(id))
        } catch (e: Exception) {
            null
        }
    }

    fun findByIds(ids: List<String>): List<User> {
        val objectIds = ids.mapNotNull {
            try {
                ObjectId(it)
            } catch (e: Exception) {
                null
            }
        }
        if (objectIds.isEmpty()) return emptyList()
        return userRepository.findAllByIds(objectIds)
    }

    fun findUsernamesByIds(ids: List<String>): Map<String, String> {
        val users = findByIds(ids)
        return users.associate { it.id!!.toHexString() to (it.username ?: "Unknown") }
    }
}
