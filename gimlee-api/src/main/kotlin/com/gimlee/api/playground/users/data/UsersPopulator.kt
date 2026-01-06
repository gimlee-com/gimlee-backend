package com.gimlee.api.playground.users.data

import com.gimlee.api.playground.users.createUsers
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.User.Companion.FIELD_USERNAME
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import com.gimlee.auth.domain.UserStatus
import com.gimlee.payments.crypto.piratechain.domain.PirateChainAddressService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UsersPopulator(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val pirateChainAddressService: PirateChainAddressService
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val PASSWORD = "Password1"
        private const val PHONE = "123456789"
        private const val EMAIL = "playground-user@gimlee.com"
    }

    fun populateUsers(viewKey: String? = null): List<Pair<User, List<Role>>> {
        if (viewKey != null) {
            return listOf(createSeller(viewKey))
        }

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

    private fun createSeller(viewKey: String): Pair<User, List<Role>> {
        val username = "seller"
        val existingUser = userRepository.findOneByField(FIELD_USERNAME, username)
        val user = if (existingUser != null) {
            log.info("User 'seller' already exists. Reusing.")
            existingUser
        } else {
            val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
            val newUser = User(
                username = username,
                displayName = username,
                phone = PHONE,
                email = EMAIL,
                password = passwordHash,
                passwordSalt = salt,
                status = UserStatus.ACTIVE,
                lastLogin = LocalDateTime.now()
            )
            val savedUser = userRepository.save(newUser)
            log.info("User 'seller' created.")
            savedUser
        }

        val existingRoles = userRoleRepository.getAll(user.id!!)
        val rolesToAdd = listOf(Role.USER, Role.PIRATE).filter { it !in existingRoles }
        rolesToAdd.forEach { role ->
            userRoleRepository.add(user.id!!, role)
        }
        if (rolesToAdd.isNotEmpty()) {
            log.info("Roles $rolesToAdd added to user 'seller'.")
        }

        pirateChainAddressService.importAndAssociateViewKey(user.id!!.toHexString(), viewKey)
        log.info("ViewKey registered for user 'seller'.")

        return Pair(user, listOf(Role.USER, Role.PIRATE))
    }
}