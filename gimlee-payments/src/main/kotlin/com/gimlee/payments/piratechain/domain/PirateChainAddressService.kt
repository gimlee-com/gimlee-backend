package com.gimlee.payments.piratechain.domain

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.client.PirateChainRpcClient.PirateChainRpcException
import com.gimlee.payments.piratechain.persistence.model.PirateChainAddressInfo
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import com.gimlee.payments.piratechain.util.anonymize

@Service
class PirateChainAddressService(
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository,
    private val pirateChainRpcClient: PirateChainRpcClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Imports a viewing key into the Pirate Chain node and associates the resulting
     * primary z-address with the given user ID in the repository.
     *
     * @param userId The ID of the user.
     * @param viewKey The viewing key provided by the user.
     * @throws PirateChainRpcException if interaction with the Pirate Chain node fails.
     * @throws IllegalStateException if the viewing key does not yield any addresses after import.
     * @throws RuntimeException for database errors.
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

        val addressInfo = rpcResponse.result?.let {
            PirateChainAddressInfo(
                zAddress = it.address,
                viewKey = viewKey,
            )
        } ?: error("Successfully added view key for user: $userId, but the associated address was null!")

        try {
            userPirateChainAddressRepository.addAddressToUser(ObjectId(userId), addressInfo)
            log.info(
                "Successfully associated view key {} and address {} for user ID: {}",
                anonymize(viewKey),
                anonymize(addressInfo.zAddress),
                userId
            )
        } catch (e: Exception) {
            log.error(
                "Failed to add address info for user {} (zAddress {}, viewKey {}): {}",
                userId,
                anonymize(addressInfo.zAddress),
                anonymize(viewKey),
                e.message,
                e
            )
            throw RuntimeException("Failed to store Pirate Chain address information.", e)
        }

    }
}