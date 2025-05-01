package com.gimlee.api.playground.users.data

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import com.gimlee.auth.model.Role
import com.gimlee.api.auth.persistence.UserRepository
import com.gimlee.api.auth.persistence.UserRoleRepository
import com.gimlee.api.auth.domain.User
import java.time.Duration
import kotlin.random.Random

private val userIdsByRoleCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(1))
    .build<Role, List<String>>()

@Lazy(true)
@Component
class PlaygroundUsersRepository(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository
) {

    fun getRandomUserByRole(role: Role): User {
        val userIdsWithRole = userIdsByRoleCache.get(role) {
            userRoleRepository.findAllByField("role", role).map { userRole -> userRole.userId.toHexString() }
        } ?: error("No users found with role: $role")
        val randomUserId = userIdsWithRole[Random.nextInt(0, userIdsWithRole.size)]
        return userRepository.findOneByField("id", randomUserId, includeCredentials = false)
            ?: error("No users found with role: $role")
    }
}