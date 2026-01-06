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
import com.gimlee.payments.crypto.ycash.domain.YcashAddressService
import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UsersPopulator(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val pirateChainAddressService: PirateChainAddressService,
    private val ycashAddressService: YcashAddressService
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val PASSWORD = "Password1"
        private const val PHONE = "123456789"
        private const val EMAIL = "playground-user@gimlee.com"
    }

    fun populateUsers(pirateViewKey: String? = null, ycashViewKey: String? = null): List<Pair<User, List<Role>>> {
        val sellers = mutableListOf<Pair<User, List<Role>>>()
        if (pirateViewKey != null) {
            sellers.add(createSeller("pirate_seller", pirateViewKey, Role.PIRATE) { userId, vk ->
                pirateChainAddressService.importAndAssociateViewKey(userId, vk)
            })
        }
        if (ycashViewKey != null) {
            sellers.add(createSeller("ycash_seller", ycashViewKey, Role.YCASH) { userId, vk ->
                ycashAddressService.importAndAssociateViewKey(userId, vk)
            })
        }

        if (sellers.isNotEmpty()) {
            return sellers
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

    private fun createSeller(username: String, viewKey: String, role: Role, importFunc: (String, String) -> Unit): Pair<User, List<Role>> {
        val existingUser = userRepository.findOneByField(FIELD_USERNAME, username)
        val user = if (existingUser != null) {
            log.info("User '$username' already exists. Reusing.")
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
            log.info("User '$username' created.")
            savedUser
        }

        val existingRoles = userRoleRepository.getAll(user.id!!)
        val rolesToAdd = listOf(Role.USER, role).filter { it !in existingRoles }
        rolesToAdd.forEach { r ->
            userRoleRepository.add(user.id!!, r)
        }
        if (rolesToAdd.isNotEmpty()) {
            log.info("Roles $rolesToAdd added to user '$username'.")
        }

        importFunc(user.id!!.toHexString(), viewKey)
        log.info("ViewKey registered for user '$username'.")

        return Pair(user, listOf(Role.USER, role))
    }
}