package com.gimlee.payments.crypto.web.dto

import com.gimlee.payments.crypto.client.model.ReceivedTransaction

/**
 * DTO representing a cryptocurrency transaction for API responses.
 */
data class CryptoTransactionDto(
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
                CryptoTransactionDto(
                    txid,
                    memo,
                    amount,
                    confirmations,
                    zAddress
                )
            }
    }
}
