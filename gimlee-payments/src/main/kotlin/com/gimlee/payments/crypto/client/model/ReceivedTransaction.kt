package com.gimlee.payments.crypto.client.model

import com.gimlee.payments.crypto.util.MemoDecoder.decodeHexMemo

data class RawReceivedTransaction(
    val txid: String,
    val memo: String?, // Receives the raw memo string (hex)
    val amount: Double,
    val confirmations: Int
) {
    fun toReceivedTransaction() = ReceivedTransaction(
        txid,
        decodeHexMemo(memo),
        amount,
        confirmations
    )
}

data class ReceivedTransaction(
    val txid: String,
    val memo: String?,
    val amount: Double,
    val confirmations: Int
)
