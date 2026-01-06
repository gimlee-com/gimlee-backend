package com.gimlee.payments.crypto.domain
import com.gimlee.common.domain.model.Currency

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import com.gimlee.payments.crypto.client.CryptoClient
import com.gimlee.payments.crypto.client.model.RawReceivedTransaction
import com.gimlee.payments.client.model.RpcResponse
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.web.dto.CryptoTransactionDto

abstract class CryptoPaymentService(
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val cryptoClient: CryptoClient,
    private val cryptoCurrency: Currency
) {

    companion object {
        private val log = LoggerFactory.getLogger(CryptoPaymentService::class.java)
        private const val MIN_CONFIRMATIONS = 1
    }

    /**
     * Retrieves transactions for all known Z-addresses associated with a given user ID and cryptocurrency type.
     */
    fun getUserTransactions(userId: String): List<CryptoTransactionDto> {
        log.debug("Fetching {} transactions for user ID: {}", cryptoCurrency, userId)
        val userObjectId = try {
            ObjectId(userId)
        } catch (_: IllegalArgumentException) {
            log.warn("Invalid user ID format provided: {} (ObjectId was expected)", userId)
            return emptyList()
        }

        val userAddressesDoc = userWalletAddressRepository.findByUserId(userObjectId)
        if (userAddressesDoc == null || userAddressesDoc.addresses.isEmpty()) {
            log.info("No {} addresses found for user ID: {}", cryptoCurrency, userId)
            return emptyList()
        }

        val relevantAddresses = userAddressesDoc.addresses.filter { it.type == cryptoCurrency }
        if (relevantAddresses.isEmpty()) {
            log.info("No {} addresses found for user ID: {}", cryptoCurrency, userId)
            return emptyList()
        }

        val allTransactions = mutableListOf<CryptoTransactionDto>()

        relevantAddresses.forEach { addressInfo ->
            val zAddress = addressInfo.zAddress
            log.debug("Querying RPC for {} transactions for address: {}", cryptoCurrency, zAddress)
            try {
                val response : RpcResponse<List<RawReceivedTransaction>> =
                    cryptoClient.getReceivedByAddress(zAddress, MIN_CONFIRMATIONS)

                response.result?.let { transactions ->
                    log.debug("Found {} transactions for {} address {}", transactions.size, cryptoCurrency, zAddress)
                    allTransactions.addAll(transactions.map { tx ->
                        CryptoTransactionDto.fromReceivedTransaction(
                            tx.toReceivedTransaction(),
                            zAddress
                        )
                    })
                } ?: log.warn("Received null result for {} transactions call for address {} user {}", cryptoCurrency, zAddress, userId)

                response.error?.let {
                    log.error("RPC error fetching {} transactions for address {}: Code={}, Message={}",
                        cryptoCurrency, zAddress, it["code"], it["message"])
                }
            } catch (e: Exception) {
                log.error("Unexpected error fetching {} transactions for address {} user {}: {}", cryptoCurrency, zAddress, userId, e.message, e)
                throw RuntimeException("Unexpected error fetching $cryptoCurrency transactions for address $zAddress.", e)
            }
        }

        allTransactions.sortByDescending { it.confirmations }

        log.debug("Returning {} total {} transactions across {} addresses for user ID: {}",
                 allTransactions.size, cryptoCurrency, relevantAddresses.size, userId)
        return allTransactions
    }
}
