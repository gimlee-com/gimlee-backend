package com.gimlee.payments.crypto.client.model

data class CryptoInfo(
    val version: Int,
    val protocolversion: Int,
    val walletversion: Int,
    val balance: Double,
    val blocks: Long,
    val timeoffset: Int,
    val connections: Int,
    val proxy: String?,
    val difficulty: Double,
    val testnet: Boolean,
    val keypoololdest: Long,
    val keypoolsize: Int,
    val paytxfee: Double,
    val relayfee: Double,
    val errors: String?
)
