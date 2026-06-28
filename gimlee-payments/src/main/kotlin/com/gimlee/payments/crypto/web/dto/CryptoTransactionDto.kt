package com.gimlee.payments.crypto.web.dto

import com.gimlee.payments.crypto.client.model.ReceivedTransaction
import com.gimlee.payments.crypto.persistence.model.IncomingTransactionDocument
import com.gimlee.common.InstantUtils.fromMicros
import java.time.Instant

/**
 * DTO representing a cryptocurrency transaction for API responses.
 */
data class CryptoTransactionDto(
    val txid: String,
    val amount: Double,
    val confirmations: Int,
    val currency: String,
    val timestamp: Instant,
    val memo: String?,
    val address: String, // The recipient address (zAddress for ARRR/YEC)
    val explorerUrl: String? = null
) {
    companion object {
        fun fromReceivedTransaction(
            receivedTransaction: ReceivedTransaction,
            currency: String,
            address: String,
            timestamp: Instant = Instant.now(),
            explorerUrl: String? = null
        ) =
            with(receivedTransaction) {
                CryptoTransactionDto(
                    txid = txid,
                    amount = amount,
                    confirmations = confirmations,
                    currency = currency,
                    timestamp = timestamp,
                    memo = memo,
                    address = address,
                    explorerUrl = explorerUrl
                )
            }

        fun fromDocument(
            doc: IncomingTransactionDocument,
            explorerUrl: String? = null
        ) =
            CryptoTransactionDto(
                txid = doc.txid,
                amount = doc.amount,
                confirmations = doc.confirmations,
                currency = doc.type.name,
                timestamp = fromMicros(doc.detectedAtMicros),
                memo = doc.memo,
                address = doc.address,
                explorerUrl = explorerUrl?.replace("{txid}", doc.txid)
            )
    }
}
