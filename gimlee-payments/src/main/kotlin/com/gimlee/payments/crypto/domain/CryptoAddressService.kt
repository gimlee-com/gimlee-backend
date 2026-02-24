package com.gimlee.payments.crypto.domain
import com.gimlee.common.domain.model.Currency

import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.toMicros
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import com.gimlee.payments.crypto.client.CryptoClient
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.util.anonymize
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

abstract class CryptoAddressService(
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val cryptoClient: CryptoClient,
    private val userRoleRepository: UserRoleRepository,
    private val cryptoCurrency: Currency,
    private val requiredRole: Role,
    private val supportedAddressTypes: Set<WalletShieldedAddressType>
) {

    companion object {
        private val log = LoggerFactory.getLogger(CryptoAddressService::class.java)

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
        log.info("Importing and associating view key for {} user ID: {}", cryptoCurrency, userId)

        val rpcResponse = try {
            cryptoClient.importViewingKey(viewKey).also {
                log.info("Successfully submitted {} view key import request for user ID: {}", cryptoCurrency, userId)
                log.debug("Associated zAddress for {}: {}", userId, it.result)
            }
        } catch (e: Exception) {
            log.error("Error importing {} view key for user {}: {}", cryptoCurrency, userId, e.message, e)
            throw e
        }

        val (viewKeyHash, viewKeySalt) = try {
            hashViewKey(viewKey)
        } catch (e: Exception) {
            log.error("Error hashing {} view key for user {}: {}", cryptoCurrency, userId, e.message, e)
            throw RuntimeException("Failed to hash view key.", e)
        }

        val currentTimestampMicros = Instant.now().toMicros()

        val addressInfo = rpcResponse.result?.let {
            val importedAddressTypeName = it.type.lowercase()
            val importedAddressType = WalletShieldedAddressType.entries.firstOrNull { addressType ->
                addressType.rpcName == importedAddressTypeName
            }
            if (importedAddressType !in supportedAddressTypes) {
                throw UnsupportedViewingKeyAddressTypeException(
                    currency = cryptoCurrency,
                    addressType = importedAddressTypeName,
                    supportedAddressTypes = supportedAddressTypes
                )
            }
            val validatedAddressType = importedAddressType
                ?: throw UnsupportedViewingKeyAddressTypeException(
                    currency = cryptoCurrency,
                    addressType = importedAddressTypeName,
                    supportedAddressTypes = supportedAddressTypes
                )
            WalletAddressInfo(
                type = cryptoCurrency,
                addressType = validatedAddressType,
                zAddress = it.address,
                viewKeyHash = viewKeyHash,
                viewKeySalt = viewKeySalt,
                lastUpdateTimestamp = currentTimestampMicros
            )
        } ?: error("Successfully added $cryptoCurrency view key for user: $userId, but the associated address was null!")

        try {
            userWalletAddressRepository.addAddressToUser(ObjectId(userId), addressInfo)
            log.info(
                "Successfully processed {} view key for address {} for user ID: {}",
                cryptoCurrency,
                anonymize(addressInfo.zAddress),
                userId
            )
            userRoleRepository.add(ObjectId(userId), requiredRole)
        } catch (e: Exception) {
            log.error(
                "Failed to add/update {} address info for user {} (zAddress {}): {}",
                cryptoCurrency,
                userId,
                anonymize(addressInfo.zAddress),
                e.message,
                e
            )
            throw RuntimeException("Failed to store/update $cryptoCurrency address information due to: ${e.message}", e)
        }
    }
}
