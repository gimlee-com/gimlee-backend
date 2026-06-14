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
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import kotlin.math.ceil

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
        private const val EMAIL_TEMPLATE = "playground-seller-%s@gimlee.com"
        private const val PIRATE_CSV = "playground/wallets-pirate.csv"
        private const val YCASH_CSV = "playground/wallets-ycash.csv"
    }

    fun populateUsers(): List<Pair<User, List<Role>>> {
        val result = mutableListOf<Pair<User, List<Role>>>()
        result.addAll(createSellersFromCsv())

        val users = createUsers()
        users.forEach { (user, roles) ->
            val existingUser = userRepository.findOneByField(FIELD_USERNAME, user.username!!)
                ?: userRepository.findOneByField(User.FIELD_EMAIL, user.email!!)
            
            val savedUser = if (existingUser != null) {
                existingUser
            } else {
                userRepository.save(user)
            }

            val existingRoles = userRoleRepository.getAll(savedUser.id!!)
            roles.forEach { role ->
                if (role !in existingRoles) {
                    userRoleRepository.add(savedUser.id!!, role)
                }
            }
            result.add(Pair(savedUser, roles))
        }
        return result
    }

    private fun createSellersFromCsv(): List<Pair<User, List<Role>>> {
        val pirateKeys = readViewKeysFromCsv(PIRATE_CSV)
        val ycashKeys = readViewKeysFromCsv(YCASH_CSV)

        if (pirateKeys.isEmpty() && ycashKeys.isEmpty()) {
            log.warn("No wallet keys found in CSV files.")
            return emptyList()
        }

        val minRecords = minOf(pirateKeys.size, ycashKeys.size)
        val dualKeySellersCount = ceil(minRecords / 2.0).toInt()

        val results = mutableListOf<Pair<User, List<Role>>>()

        // Create dual key sellers
        for (i in 0 until dualKeySellersCount) {
            val username = "seller-pirate-ycash-${i + 1}"
            results.add(createOrUpdateSeller(username, pirateKeys[i], ycashKeys[i]))
        }

        // Create remaining pirate sellers
        for (i in dualKeySellersCount until pirateKeys.size) {
            val username = "seller-pirate-${i + 1}"
            results.add(createOrUpdateSeller(username, pirateKeys[i], null))
        }

        // Create remaining ycash sellers
        for (i in dualKeySellersCount until ycashKeys.size) {
            val username = "seller-ycash-${i + 1}"
            results.add(createOrUpdateSeller(username, null, ycashKeys[i]))
        }

        return results
    }

    private fun readViewKeysFromCsv(path: String): List<String> {
        return try {
            val resource = ClassPathResource(path)
            if (!resource.exists()) return emptyList()
            BufferedReader(InputStreamReader(resource.inputStream)).use { reader ->
                reader.lineSequence()
                    .drop(1) // header
                    .filter { it.isNotBlank() }
                    .map { line ->
                        // Simple CSV split, assuming viewing_key is the 3rd column (index 2)
                        val parts = line.split(",")
                        if (parts.size >= 3) parts[2].trim().removeSurrounding("\"") else null
                    }
                    .filterNotNull()
                    .toList()
            }
        } catch (e: Exception) {
            log.error("Failed to read CSV $path", e)
            emptyList()
        }
    }

    private fun createOrUpdateSeller(username: String, pirateViewKey: String?, ycashViewKey: String?): Pair<User, List<Role>> {
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
                email = EMAIL_TEMPLATE.format(username),
                password = passwordHash,
                passwordSalt = salt,
                status = UserStatus.ACTIVE,
                lastLogin = LocalDateTime.now()
            )
            val existingEmailUser = userRepository.findOneByField(User.FIELD_EMAIL, newUser.email!!)
            val savedUser = if (existingEmailUser != null) {
                log.info("User with email '${newUser.email}' already exists. Reusing.")
                existingEmailUser
            } else {
                userRepository.save(newUser)
            }
            log.info("User '$username' created or reused.")
            savedUser
        }

        val targetRoles = mutableListOf(Role.USER)
        if (pirateViewKey != null) targetRoles.add(Role.PIRATE)
        if (ycashViewKey != null) targetRoles.add(Role.YCASH)

        val existingRoles = userRoleRepository.getAll(user.id!!)
        targetRoles.forEach { role ->
            if (role !in existingRoles) {
                userRoleRepository.add(user.id!!, role)
            }
        }

        val userId = user.id!!.toHexString()
        if (pirateViewKey != null) {
            pirateChainAddressService.importAndAssociateViewKey(userId, pirateViewKey)
            log.info("Pirate view key registered for user '$username'.")
        }
        if (ycashViewKey != null) {
            ycashAddressService.importAndAssociateViewKey(userId, ycashViewKey)
            log.info("Ycash view key registered for user '$username'.")
        }

        return Pair(user, targetRoles)
    }
}
