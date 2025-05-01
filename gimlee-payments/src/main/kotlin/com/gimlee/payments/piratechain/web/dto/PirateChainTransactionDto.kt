package com.gimlee.payments.piratechain.web.dto

import com.gimlee.payments.piratechain.client.model.ReceivedTransaction

/**
 * DTO representing a Pirate Chain transaction for API responses.
 */
data class PirateChainTransactionDto(
    val txid: String,
    val memo: String?,
    val amount: Double,
    val confirmations: Int,
    val zAddress: String // The address this transaction was received on
) {
    companion object {
        fun fromReceivedTransaction(
            receivedTransaction: ReceivedTransaction,
            zAddress: String,
        ) =
            with(receivedTransaction) {
                PirateChainTransactionDto(
                    txid,
                    memo,
                    amount,
                    confirmations,
                    zAddress
                )
            }
    }
}