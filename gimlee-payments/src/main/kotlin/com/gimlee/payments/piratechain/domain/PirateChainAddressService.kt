package com.gimlee.payments.piratechain.domain

import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.toMicros
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.client.PirateChainRpcClient.PirateChainRpcException
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import com.gimlee.payments.piratechain.util.anonymize
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Service
class PirateChainAddressService(
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository,
    private val pirateChainRpcClient: PirateChainRpcClient,
    private val userRoleRepository: UserRoleRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        // Constants for PBKDF2
        private const val PBKDF2_ITERATIONS = 256
        private const val PBKDF2_KEY_LENGTH = 512
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512"
        private const val SALT_SIZE_BYTES = 16 // 128-bit salt
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES)
        random.nextBytes(salt)
        return salt
    }

    private fun hashViewKey(viewKey: String): Pair<String, String> {
        val salt = generateSalt()
        val spec: KeySpec = PBEKeySpec(viewKey.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        val encoder = Base64.getEncoder()
        return Pair(encoder.encodeToString(hash), encoder.encodeToString(salt))
    }

    /**
     * Imports a viewing key, hashes it, generates a timestamp, and associates the
     * z-address with the user, applying optimistic locking logic in the repository.
     */
    fun importAndAssociateViewKey(userId: String, viewKey: String) {
        log.info("Importing and associating view key for user ID: {}", userId)

        val rpcResponse = try {
            pirateChainRpcClient.importViewingKey(viewKey).also {
                log.info("Successfully submitted view key import request for user ID: {}", userId)
                log.debug("Associated zAddress for {}: {}", userId, it.result)
            }
        } catch (e: PirateChainRpcException) {
            log.error("RPC error importing view key for user {}: {}", userId, e.message, e)
            throw e
        } catch (e: Exception) {
            log.error("Unexpected error during view key import for user {}: {}", userId, e.message, e)
            throw RuntimeException("Unexpected error during view key import.", e)
        }

        val (viewKeyHash, viewKeySalt) = try {
            hashViewKey(viewKey)
        } catch (e: Exception) {
            log.error("Error hashing view key for user {}: {}", userId, e.message, e)
            throw RuntimeException("Failed to hash view key.", e)
        }

        val currentTimestampMicros = Instant.now().toMicros()

        val addressInfo = rpcResponse.result?.let {
            PirateChainAddressInfo(
                zAddress = it.address,
                viewKeyHash = viewKeyHash,
                viewKeySalt = viewKeySalt,
                lastUpdateTimestamp = currentTimestampMicros
            )
        } ?: error("Successfully added view key for user: $userId, but the associated address was null!")

        try {
            userPirateChainAddressRepository.addAddressToUser(ObjectId(userId), addressInfo)
            log.info(
                "Successfully processed view key for address {} for user ID: {}",
                anonymize(addressInfo.zAddress),
                userId
            )
            userRoleRepository.add(ObjectId(userId), Role.PIRATE)
        } catch (e: Exception) {
            log.error(
                "Failed to add/update address info for user {} (zAddress {}): {}",
                userId,
                anonymize(addressInfo.zAddress),
                e.message,
                e
            )
            throw RuntimeException("Failed to store/update Pirate Chain address information due to: ${e.message}", e)
        }
    }
}
