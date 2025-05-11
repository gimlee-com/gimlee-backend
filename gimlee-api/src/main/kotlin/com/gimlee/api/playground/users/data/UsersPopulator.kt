package com.gimlee.api.playground.users.data

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.api.playground.users.createUsers
import com.gimlee.auth.domain.User

@Component
class UsersPopulator(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository
) {
    companion object {
        private val log = LogManager.getLogger()
    }

    fun populateUsers(): List<Pair<User, List<Role>>> {
        val users = createUsers()
        if (userRepository.findOneByField(FIELD_USERNAME, users.first().first.username!!) != null) {
            log.warn("Dummy users are already created. Returning empty list.")
            return emptyList()
        }
        return users.map { user ->
            Pair(userRepository.save(user.first), user.second)
        }.map { persistedUserAndRoles ->
            persistedUserAndRoles.second.forEach { role ->
                userRoleRepository.add(persistedUserAndRoles.first.id!!, role)
            }
            persistedUserAndRoles
        }
    }
}