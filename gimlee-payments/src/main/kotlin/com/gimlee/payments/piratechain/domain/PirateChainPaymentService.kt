package com.gimlee.payments.piratechain.domain

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import com.gimlee.payments.piratechain.client.PirateChainRpcClient
import com.gimlee.payments.piratechain.client.model.RawReceivedTransaction
import com.gimlee.payments.piratechain.client.model.RpcResponse
import com.gimlee.payments.piratechain.persistence.UserPirateChainAddressRepository
import com.gimlee.payments.piratechain.web.dto.PirateChainTransactionDto // Import the new DTO

@Service
class PirateChainPaymentService(
    private val userPirateChainAddressRepository: UserPirateChainAddressRepository,
    private val pirateChainRpcClient: PirateChainRpcClient
) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val MIN_CONFIRMATIONS = 1
    }

    /**
     * Retrieves transactions for all known Z-addresses associated with a given user ID.
     * It fetches the addresses from the repository and then queries the Pirate Chain node
     * via RPC for transactions received by those addresses.
     *
     * @param userId The ID of the user whose transactions are to be fetched.
     * @return A list of [PirateChainTransactionDto] representing the user's transactions.
     * @throws PirateChainRpcClient.PirateChainRpcException if communication with the Pirate Chain node fails.
     * @throws RuntimeException for unexpected errors.
     */
    fun getUserTransactions(userId: String): List<PirateChainTransactionDto> {
        log.debug("Fetching transactions for user ID: {}", userId)
        val userObjectId = try {
            ObjectId(userId)
        } catch (_: IllegalArgumentException) {
            log.warn("Invalid user ID format provided: {} (ObjectId was expected)", userId)
            return emptyList()
        }

        val userAddressesDoc = userPirateChainAddressRepository.findByUserId(userObjectId)
        if (userAddressesDoc == null || userAddressesDoc.addresses.isEmpty()) {
            log.info("No Pirate Chain addresses found for user ID: {}", userId)
            return emptyList()
        }

        val allTransactions = mutableListOf<PirateChainTransactionDto>()

        userAddressesDoc.addresses.forEach { addressInfo ->
            val zAddress = addressInfo.zAddress
            log.debug("Querying RPC for transactions for address: {}", zAddress)
            try {
                val response : RpcResponse<List<RawReceivedTransaction>> =
                    pirateChainRpcClient.getReceivedByAddress(zAddress, MIN_CONFIRMATIONS)

                response.result?.let { transactions ->
                    log.debug("Found {} transactions for address {}", transactions.size, zAddress)
                    allTransactions.addAll(transactions.map { tx ->
                        PirateChainTransactionDto.fromReceivedTransaction(
                            tx.toReceivedTransaction(),
                            zAddress
                        )
                    })
                } ?: log.warn("Received null result for transactions call for address {} user {}", zAddress, userId)

                response.error?.let {
                    log.error("RPC error fetching transactions for address {}: Code={}, Message={}",
                        zAddress, it["code"], it["message"])
                }
            } catch (e: PirateChainRpcClient.PirateChainRpcException) {
                log.error("RPC Exception fetching transactions for address {} user {}: {}", zAddress, userId, e.message)
                throw e
            } catch (e: Exception) {
                log.error("Unexpected error fetching transactions for address {} user {}: {}", zAddress, userId, e.message, e)
                throw RuntimeException("Unexpected error fetching transactions for address $zAddress.", e)
            }
        }

        allTransactions.sortByDescending { it.confirmations }

        log.info("Returning {} total transactions across {} addresses for user ID: {}",
                 allTransactions.size, userAddressesDoc.addresses.size, userId)
        return allTransactions
    }
}