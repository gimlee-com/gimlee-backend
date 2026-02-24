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
        private const val SELLER_USERNAME = "playground_seller"
    }

    fun populateUsers(pirateViewKey: String? = null, ycashViewKey: String? = null): List<Pair<User, List<Role>>> {
        if (pirateViewKey != null || ycashViewKey != null) {
            return listOf(createSeller(pirateViewKey, ycashViewKey))
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

    private fun createSeller(pirateViewKey: String?, ycashViewKey: String?): Pair<User, List<Role>> {
        val existingUser = userRepository.findOneByField(FIELD_USERNAME, SELLER_USERNAME)
        val user = if (existingUser != null) {
            log.info("User '$SELLER_USERNAME' already exists. Reusing.")
            existingUser
        } else {
            val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
            val newUser = User(
                username = SELLER_USERNAME,
                displayName = SELLER_USERNAME,
                phone = PHONE,
                email = EMAIL,
                password = passwordHash,
                passwordSalt = salt,
                status = UserStatus.ACTIVE,
                lastLogin = LocalDateTime.now()
            )
            val savedUser = userRepository.save(newUser)
            log.info("User '$SELLER_USERNAME' created.")
            savedUser
        }

        val targetRoles = listOfNotNull(
            Role.USER,
            pirateViewKey?.let { Role.PIRATE },
            ycashViewKey?.let { Role.YCASH }
        )
        val existingRoles = userRoleRepository.getAll(user.id!!)
        val rolesToAdd = listOf(Role.USER).filter { it !in existingRoles }
        rolesToAdd.forEach { r ->
            userRoleRepository.add(user.id!!, r)
        }
        if (rolesToAdd.isNotEmpty()) {
            log.info("Roles $rolesToAdd added to user '$SELLER_USERNAME'.")
        }

        val userId = user.id!!.toHexString()
        if (pirateViewKey != null) {
            pirateChainAddressService.importAndAssociateViewKey(userId, pirateViewKey)
            log.info("Pirate view key registered for user '$SELLER_USERNAME'.")
        }
        if (ycashViewKey != null) {
            ycashAddressService.importAndAssociateViewKey(userId, ycashViewKey)
            log.info("Ycash view key registered for user '$SELLER_USERNAME'.")
        }

        return Pair(user, targetRoles)
    }
}
